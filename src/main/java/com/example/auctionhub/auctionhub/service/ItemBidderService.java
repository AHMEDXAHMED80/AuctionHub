package com.example.auctionhub.auctionhub.service;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import com.example.auctionhub.auctionhub.dto.TopFiveItemResponse;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.mapper.TopFiveItemMapper;
import com.example.auctionhub.auctionhub.repository.ItemRepository;
import com.example.auctionhub.auctionhub.redis.TrackTopViewedItems;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ItemBidderService {
    
    private final ItemRepository itemRepository;
    private final TrackTopViewedItems trackTopViewedItems;
    private final ItemImageService itemImageService;
    private final TopFiveItemMapper topFiveItemMapper;
    
    public ItemBidderService(
            ItemRepository itemRepository,
            TrackTopViewedItems trackTopViewedItems,
            ItemImageService itemImageService,
            TopFiveItemMapper topFiveItemMapper
    ) {
        this.itemRepository = itemRepository;
        this.trackTopViewedItems = trackTopViewedItems;
        this.itemImageService = itemImageService;
        this.topFiveItemMapper = topFiveItemMapper;
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
}
