package com.example.auctionhub.auctionhub.service;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.example.auctionhub.auctionhub.dto.BidResponse;
import com.example.auctionhub.auctionhub.dto.ItemDetailDTO;
import com.example.auctionhub.auctionhub.dto.ItemSearchRequest;
import com.example.auctionhub.auctionhub.dto.ItemSearchResponse;
import com.example.auctionhub.auctionhub.dto.ItemSearchResultDTO;
import com.example.auctionhub.auctionhub.dto.TopFiveItemResponse;
import com.example.auctionhub.auctionhub.models.Bid;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.models.Item.ItemStatus;
import com.example.auctionhub.auctionhub.mapper.ItemDetailMapper;
import com.example.auctionhub.auctionhub.mapper.ItemSearchResultDTOMapper;
import com.example.auctionhub.auctionhub.mapper.TopFiveItemMapper;
import com.example.auctionhub.auctionhub.repository.ItemRepository;
import com.example.auctionhub.auctionhub.security.SecurityUtils;
import com.example.auctionhub.auctionhub.redis.TrackTopViewedItems;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ItemBidderService {
    
    private final ItemRepository itemRepository;
    private final TrackTopViewedItems trackTopViewedItems;
    private final ItemImageService itemImageService;
    private final TopFiveItemMapper topFiveItemMapper;
    private final ItemSearchResultDTOMapper itemSearchResultDTOMapper;
    private final ItemDetailMapper itemDetailMapper;

    public ItemBidderService(
            ItemRepository itemRepository,
            TrackTopViewedItems trackTopViewedItems,
            ItemImageService itemImageService,
            TopFiveItemMapper topFiveItemMapper,
            ItemSearchResultDTOMapper itemSearchResultDTOMapper,
            ItemDetailMapper itemDetailMapper
    ) {
        this.itemRepository = itemRepository;
        this.trackTopViewedItems = trackTopViewedItems;
        this.itemImageService = itemImageService;
        this.topFiveItemMapper = topFiveItemMapper;
        this.itemSearchResultDTOMapper = itemSearchResultDTOMapper;
        this.itemDetailMapper = itemDetailMapper;
    }
    
    /**
     * Retrieves the top 5 most viewed items and returns them with ranking and image information.
     * 
     * @return List of TopFiveItemResponse objects containing top 5 viewed items with their details,
     *         or an empty list if no items are found or an error occurs
     */
    public List<TopFiveItemResponse> getTopFiveItems() {
        try {
            log.debug("Fetching top 5 viewed items from Redis");

            // Retrieve top 5 item IDs from Redis cache based on view count
            Set<String> topItemsIdsSet = trackTopViewedItems.getTopFiveViewedItems();
            if (topItemsIdsSet == null || topItemsIdsSet.isEmpty()) {
                log.debug("No top viewed items found in Redis");
                return List.of();
            }

            // Convert string IDs from Redis to Long type for database queries
            List<Long> topItemsIds;
            try {
                topItemsIds = topItemsIdsSet.stream()
                    .map(Long::parseLong)
                    .toList();
            } catch (NumberFormatException e) {
                log.error("Invalid item ID format in Redis: {}", e.getMessage());
                return List.of();
            }

            log.debug("Found {} top viewed item IDs: {}", topItemsIds.size(), topItemsIds);

            // Fetch item entities from database using batch query for efficiency
            List<Item> topItemsObj = itemRepository.findAllById(topItemsIds);
            log.debug("Retrieved {} items from database out of {}", topItemsObj.size(), topItemsIds.size());

            // Create a map for O(1) lookup of items by ID to maintain order from Redis
            Map<Long, Item> topItemMap = topItemsObj.stream()
                .collect(Collectors.toMap(Item::getId, item -> item));

            // Fetch the first image URL for each item to display in response
            Map<Long, String> urlsForImagesFirstIndex = itemImageService.getFirstImageIndexForAllItems(topItemsIds);

            // Initialize atomic counter to assign ranking positions (1, 2, 3, etc.)
            AtomicInteger rank = new AtomicInteger(1);

            // Transform items to response DTOs with ranking and image information
            List<TopFiveItemResponse> responses = topItemsIds.stream()
                .map(itemId -> {
                    // Look up item from map (handles deleted items gracefully)
                    Item item = topItemMap.get(itemId);
                    if (item == null) {
                        log.warn("Item with ID {} not found in database (may have been deleted)", itemId);
                        return null;
                    }

                    // Map item entity to response DTO using MapStruct mapper
                    String urlImage = urlsForImagesFirstIndex.getOrDefault(itemId, "");
                    TopFiveItemResponse itemResponse = topFiveItemMapper.toTopFiveItemResponse(item);
                    itemResponse.setFirstIndexUrlImage(urlImage);
                    itemResponse.setRank(rank.getAndIncrement());
                    return itemResponse;
                })
                // Filter out null values (deleted items)
                .filter(response -> response != null)
                .toList();

            log.debug("Successfully created {} top item responses", responses.size());
            return responses;

        } catch (Exception e) {
            log.error("Error fetching top 5 viewed items: {}", e.getMessage(), e);
            return List.of();
        }
    }

    public ItemSearchResponse searchItems(ItemSearchRequest request) {
        // Validate request first (outside Specification)
        validateSearchRequest(request);

        Specification<Item> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Only active items
            predicates.add(cb.equal(root.get("status"), ItemStatus.ACTIVE));

            // Keyword search (independent filter)
            if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                String keyword = "%" + request.getKeyword().toLowerCase().trim() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), keyword),
                    cb.like(cb.lower(root.get("description")), keyword)
                ));
            }

            // Category filter (independent)
            if (request.getCategory() != null) {
                predicates.add(cb.equal(root.get("category"), request.getCategory()));
            }

            // Price range filters (independent)
            if (request.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("currentHighestBid"), request.getMinPrice()));
            }
            if (request.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("currentHighestBid"), request.getMaxPrice()));
            }

            // Ending soon filter (independent)
            if (Boolean.TRUE.equals(request.getEndingSoon())) {
                LocalDateTime now = LocalDateTime.now();
                predicates.add(cb.between(root.get("endDate"), now, now.plusHours(24)));
            }

            // Ending today filter
            if (Boolean.TRUE.equals(request.getEndingToday())) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime endOfDay = now.toLocalDate().atTime(23, 59, 59);
                predicates.add(cb.between(root.get("endDate"), now, endOfDay));
            }

            // Custom ending hours filter
            if (request.getEndingInHours() != null && request.getEndingInHours() > 0) {
                LocalDateTime now = LocalDateTime.now();
                predicates.add(cb.between(root.get("endDate"), now, now.plusHours(request.getEndingInHours())));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Sorting
        Sort sort = getSortStrategy(request.getSortBy());

        // Pagination
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 20;
        Pageable pageable = PageRequest.of(page, size, sort);

        // Execute query
        Page<Item> itemsPage = itemRepository.findAll(spec, pageable);
        List<Item> items = itemsPage.getContent();

        // Fetch first image for each item (bulk operation)
        List<Long> itemIds = items.stream().map(Item::getId).toList();
        Map<Long, String> firstImageUrls = itemImageService.getFirstImageIndexForAllItems(itemIds);

        // Map items to DTOs
        List<ItemSearchResultDTO> itemDTOs = items.stream()
                .map(item -> {
                    ItemSearchResultDTO itemDto = itemSearchResultDTOMapper.toItemSearchResponse(item);
                    Duration timeRemaining = calculateRemainingTime(item.getEndDate());
                    itemDto.setTimeRemaining(timeRemaining);
                    itemDto.setImagesUrlList(firstImageUrls.getOrDefault(item.getId(), ""));
                    return itemDto;
                })
                .toList();

        // Build response with pagination metadata
        return ItemSearchResponse.builder()
                .items(itemDTOs)
                .currentPage(itemsPage.getNumber())
                .pageSize(itemsPage.getSize())
                .totalElements(itemsPage.getTotalElements())
                .totalPages(itemsPage.getTotalPages())
                .hasNext(itemsPage.hasNext())
                .hasPrevious(itemsPage.hasPrevious())
                .appliedKeyword(request.getKeyword())
                .appliedCategory(request.getCategory() != null ? request.getCategory().toString() : null)
                .build();
    }

    /**
     * Calculate remaining time until auction ends
     */
    private Duration calculateRemainingTime(LocalDateTime endDate) {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, endDate);
    }

    /**
     * Get sorting strategy based on enum value
     */
    private Sort getSortStrategy(ItemSearchRequest.ItemSortOption sortBy) {
        if (sortBy == null) {
            return Sort.by(Sort.Direction.ASC, "endDate"); // Default: ending soonest first
        }

        return switch (sortBy) {
            case PRICE_LOW -> Sort.by(Sort.Direction.ASC, "currentHighestBid");
            case PRICE_HIGH -> Sort.by(Sort.Direction.DESC, "currentHighestBid");
            case ENDING_SOON -> Sort.by(Sort.Direction.ASC, "endDate");
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    /**
     * Validate search request parameters
     */
    private void validateSearchRequest(ItemSearchRequest request) {
        // Validate keyword length
        if (request.getKeyword() != null && request.getKeyword().length() > 100) {
            log.warn("Search keyword too long: {} characters (max 100)", request.getKeyword().length());
            throw new IllegalArgumentException("Search keyword too long (max 100 characters)");
        }

        // Validate minimum price
        if (request.getMinPrice() != null && request.getMinPrice().compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Invalid minimum price: {}", request.getMinPrice());
            throw new IllegalArgumentException("Minimum price cannot be negative");
        }

        // Validate maximum price
        if (request.getMaxPrice() != null && request.getMaxPrice().compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Invalid maximum price: {}", request.getMaxPrice());
            throw new IllegalArgumentException("Maximum price cannot be negative");
        }

        // Validate price range
        if (request.getMinPrice() != null && request.getMaxPrice() != null
            && request.getMinPrice().compareTo(request.getMaxPrice()) > 0) {
            log.warn("Invalid price range: min={} > max={}", request.getMinPrice(), request.getMaxPrice());
            throw new IllegalArgumentException("Minimum price cannot be greater than maximum price");
        }

        // Validate ending hours
        if (request.getEndingInHours() != null
            && (request.getEndingInHours() < 1 || request.getEndingInHours() > 720)) {
            log.warn("Invalid endingInHours: {} (must be between 1 and 720)", request.getEndingInHours());
            throw new IllegalArgumentException("endingInHours must be between 1 and 720 (30 days)");
        }

        log.debug("Search request validation passed");
    }


    public ItemDetailDTO getItemDetail(Long itemId) {
        User user = SecurityUtils.getCurrentUser();
        String userRole = user.getRole().name();

        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        List<String> imageUrls = itemImageService.findAllByItemIdOrderByIndexAsc(itemId)
            .stream()
            .map(img -> img.getUrl())
            .toList();

        List<Bid> allBids = item.getBids() != null ? item.getBids() : List.of();

        List<BidResponse> recentBids = allBids.stream()
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(5)
            .map(bid -> new BidResponse(
                bid.getUser().getUsername(),
                item.getTitle(),
                bid.getBidAmount(),
                bid.getCreatedAt()
            ))
            .toList();

        // Mapper handles all common fields (id, title, description, category, status,
        // startingPrice, currentHighestBid, startDate, endDate, sellerId, sellerName)
        ItemDetailDTO dto = itemDetailMapper.toDto(item);
        dto.setTimeRemaining(calculateRemainingTime(item.getEndDate()));
        dto.setImageUrls(imageUrls);
        dto.setBidCount(allBids.size());
        dto.setIsUserWatching(false);

        // Role-specific fields only
        switch (userRole) {
            case "USER" -> {
                dto.setHasUserBid(allBids.stream().anyMatch(b -> b.getUser().getId().equals(user.getId())));
                dto.setRecentBids(recentBids);
                dto.setIsUserSeller(false);
            }
            case "POSTER" -> {
                boolean isOwner = item.getSeller().getId().equals(user.getId());
                dto.setHasUserBid(false);
                dto.setRecentBids(isOwner ? recentBids : List.of());
                dto.setIsUserSeller(isOwner);
            }
            case "ADMIN" -> {
                dto.setHasUserBid(false);
                dto.setRecentBids(recentBids);
                dto.setIsUserSeller(false);
            }
            default -> throw new IllegalArgumentException("Unknown role: " + userRole);
        }

        return dto;
    }

}
