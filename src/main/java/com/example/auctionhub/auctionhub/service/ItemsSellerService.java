package com.example.auctionhub.auctionhub.service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.List;

import com.example.auctionhub.auctionhub.dto.ItemSellerRequest;
import com.example.auctionhub.auctionhub.mapper.ItemSellerMapper;
import com.example.auctionhub.auctionhub.dto.ItemResponse;
import com.example.auctionhub.auctionhub.models.ItemImages;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.repository.ItemRepository;
import com.example.auctionhub.auctionhub.security.SecurityUtils;


/**
 * Service for managing seller item operations, including creation and retrieval of items for the authenticated user.
 * Handles mapping, persistence, and image upload logic for items.
 */
@Service
public class ItemsSellerService {

    private static final Logger log = LoggerFactory.getLogger(ItemsSellerService.class);

    /**
     * Mapper for converting Item entities and requests.
     */
    private final ItemSellerMapper itemSellerMapper;

    /**
     * Repository for item persistence operations.
     */
    private final ItemRepository itemRepository;

    /**
     * Service for item image operations.
     */
    private final ItemImageService itemImageService;

    /**
     * Constructs an ItemsSellerService with required dependencies.
     *
     * @param itemSellerMapper      Mapper for Item and ItemSellerRequest
     * @param itemRepository        Repository for Item persistence
     * @param itemImageService      Service for ItemImages operations
     */
    public ItemsSellerService(ItemSellerMapper itemSellerMapper, ItemRepository itemRepository, ItemImageService itemImageService) {
        this.itemSellerMapper = itemSellerMapper;
        this.itemRepository = itemRepository;
        this.itemImageService = itemImageService;
    }

    /**
     * Returns all items listed by the currently authenticated user.
     *
     * @return List of ItemResponse DTOs for the user's items
     */
    @Transactional(readOnly = true)
    public List<ItemResponse> getAllItemsForCurrentUser() {
        User currentUser = SecurityUtils.getAuthenticatedUserOrThrow();
        List<Item> userItems = itemRepository.findAllBySellerId(currentUser.getId());
        return userItems.stream().map(itemSellerMapper::toItemResponse)
                                .toList();
    }

    /**
     * Creates a new item for the authenticated seller, including image upload and persistence.
     *
     * @param request ItemSellerRequest containing item details and images
     * @return ItemResponse DTO for the created item
     */
    @Transactional
    public ItemResponse createItem(ItemSellerRequest request) {
        try {
            User currentUser = SecurityUtils.getAuthenticatedUserOrThrow();
            Item item = itemSellerMapper.mapToItem(request);
            item.setCurrentHighestBid(BigDecimal.ZERO);
            item.setSeller(currentUser);
            item.setStatus(Item.ItemStatus.ACTIVE);
            List<ItemImages> images = itemImageService.uploadImages(request, item);
            item.setItemImages(images);
            itemRepository.save(item);
            itemImageService.saveAllImages(images);
            return itemSellerMapper.toItemResponse(item);
        } catch (IllegalArgumentException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating item: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create item", e);
        }
    }

    @Transactional
    public ItemResponse editItem(Long itemId, ItemSellerRequest request) {
        try {
            User currentUser = SecurityUtils.getAuthenticatedUserOrThrow();
            Item item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new IllegalArgumentException("Item not found"));

            if (!isOwner(currentUser, item)) {
                throw new AccessDeniedException("Access denied");
            }

            if (request.getTitle() != null) {
                item.setTitle(request.getTitle());
            }

            if (request.getDescription() != null) {
                item.setDescription(request.getDescription());
            }

            List<ItemImages> imgs = null;
            if (request.getImagesUrlList() != null) {
                imgs = itemImageService.uploadImages(request, item);
                item.setItemImages(imgs);
            } else {
                imgs = itemImageService.findAllByItemIdOrderByIndexAsc(item.getId());
            }

            itemRepository.save(item);
            return itemSellerMapper.toItemResponse(item);
        } catch (IllegalArgumentException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error editing item {}: {}", itemId, e.getMessage(), e);
            throw new RuntimeException("Failed to edit item " + itemId, e);
        }
    }

    public boolean isOwner(User user, Item item){
        return user != null && item != null && user.getId().equals(item.getSeller().getId());
    }
}
