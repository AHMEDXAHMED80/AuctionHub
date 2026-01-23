package com.example.auctionhub.auctionhub.service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.auctionhub.auctionhub.dto.ItemSellerRequest;
import com.example.auctionhub.auctionhub.mapper.ItemSellerMapper;
import com.example.auctionhub.auctionhub.dto.ItemResponse;
import com.example.auctionhub.auctionhub.models.ItemImages;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.repository.ItemRepository;
import com.example.auctionhub.auctionhub.repository.ItemImagesRepository;
import com.example.auctionhub.auctionhub.security.SecurityUtils;
import java.util.Arrays;


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
     * Repository for item image persistence operations.
     */
    private final ItemImagesRepository itemImagesRepository;

    /**
     * Constructs an ItemsSellerService with required dependencies.
     *
     * @param itemSellerMapper      Mapper for Item and ItemSellerRequest
     * @param itemRepository        Repository for Item persistence
     * @param itemImagesRepository  Repository for ItemImages persistence
     */
    public ItemsSellerService(ItemSellerMapper itemSellerMapper, ItemRepository itemRepository, ItemImagesRepository itemImagesRepository) {
        this.itemSellerMapper = itemSellerMapper;
        this.itemRepository = itemRepository;
        this.itemImagesRepository = itemImagesRepository;
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
    public ItemResponse createItem(ItemSellerRequest request){
        User currentUser = SecurityUtils.getAuthenticatedUserOrThrow();
        Item item = itemSellerMapper.mapToItem(request);
        item.setCurrentHighestBid(BigDecimal.ZERO);
        item.setSeller(currentUser);
        item.setStatus(Item.ItemStatus.ACTIVE);
        List<ItemImages> images = uploadImages(request, item);
        item.setItemImages(images);
        itemRepository.save(item);
        itemImagesRepository.saveAll(images);
        return itemSellerMapper.toItemResponse(item);
    }

    /**
     * Uploads images for the given item based on the request.
     *
     * @param request ItemSellerRequest containing image URLs
     * @param item    The item to associate images with
     * @return List of ItemImages entities
     */
    private List<ItemImages> uploadImages(ItemSellerRequest request, Item item) {
        List<ItemSellerRequest.ItemImages> urls = request.getImagesUrlList();
        if (item.getId() == null) {
            List<ItemImages> newImagesList = new ArrayList<>();
            urls = uploadImagesForNewItems(request);
            for (int i = 0; i < urls.size(); i++) {
                ItemSellerRequest.ItemImages imgDto = urls.get(i);
                ItemImages img = new ItemImages();
                img.setUrl(imgDto.getUrl());
                img.setIndex(imgDto.getIndex());
                img.setItem(item);
                newImagesList.add(img);
            }
            return newImagesList;
        } else {
            List<ItemImages> existingImgList = itemImagesRepository.findAllByItemIdOrderByIndexAsc(item.getId());
            urls = uploadImagesForExistingItems(request, item, existingImgList);
            for (int i = 0; i < urls.size(); i++) {
                ItemSellerRequest.ItemImages imgDto = urls.get(i);
                ItemImages img = new ItemImages();
                img.setUrl(imgDto.getUrl());
                img.setIndex(imgDto.getIndex());
                img.setItem(item);
                itemImagesRepository.save(img);
                existingImgList.add(img);
            }
            if (existingImgList.size() > 6) {
                throw new IllegalArgumentException("A maximum of 6 images is allowed");
            }
            return existingImgList;
        }
    }

    private List<ItemSellerRequest.ItemImages> uploadImagesForNewItems(ItemSellerRequest request) {
        List<ItemSellerRequest.ItemImages> urls = request.getImagesUrlList();
        if (urls == null || urls.size() < 1) {
            throw new IllegalArgumentException("The item must include at least 1 picture");
        }
        if (urls.size() > 6) {
            throw new IllegalArgumentException("A maximum of 6 images is allowed");
        }
        for (ItemSellerRequest.ItemImages img : urls) {
            int idx = img.getIndex();
            if (idx < 0 || idx > 5) {
                throw new IllegalArgumentException("Image index must be between 0 and 5: " + idx);
            }
        }
        validateNewItemImageIndexes(urls);
        return urls;
    }


    private List<ItemSellerRequest.ItemImages> uploadImagesForExistingItems(ItemSellerRequest request, Item item, List<ItemImages> existingImgs) {
        List<ItemSellerRequest.ItemImages> urls = request.getImagesUrlList();
        if (urls == null || urls.size() < 1) {
            throw new IllegalArgumentException("The item must include at least 1 picture");
        }
        int imgCount = existingImgs.size();
        if (imgCount == 6) {
            throw new IllegalArgumentException("Already have 6 pictures please remove pictures");
        }
        if (urls.size() > 6) {
            throw new IllegalArgumentException("A maximum of 6 images is allowed");
        }
        for (ItemSellerRequest.ItemImages img : urls) {
            int idx = img.getIndex();
            if (idx < 0 || idx > 5) {
                throw new IllegalArgumentException("Image index must be between 0 and 5: " + idx);
            }
        }
        validateExistingItemImageIndexes(item.getId(), urls, existingImgs);
        return urls;
    }


    // Validate duplicate indexes for new items (in-memory)
    private void validateNewItemImageIndexes(List<ItemSellerRequest.ItemImages> urls) {
        Set<Integer> seenIndexes = new HashSet<>();
        for (ItemSellerRequest.ItemImages img : urls) {
            int idx = img.getIndex();
            if (!seenIndexes.add(idx)) {
                throw new IllegalArgumentException("Duplicate image index in request: " + idx);
            }
        }
    }

    // Validate duplicate indexes for existing items (DB)
    private void validateExistingItemImageIndexes(Long itemId, List<ItemSellerRequest.ItemImages> urls, List<ItemImages> imgs) {
        Set<Integer> seenIndexes = new HashSet<>();
        // Fetch all existing image indexes for this item in one query
        List<ItemImages> existingImages = imgs;
        Set<Integer> existingIndexes = new HashSet<>();
        for (ItemImages img : existingImages) {
            existingIndexes.add(img.getIndex());
        }
        for (ItemSellerRequest.ItemImages img : urls) {
            int idx = img.getIndex();
            if (!seenIndexes.add(idx)) {
                throw new IllegalArgumentException("Duplicate image index in request: " + idx);
            }
            if (existingIndexes.contains(idx)) {
                throw new IllegalArgumentException("Image index already exists for this item: " + idx);
            }
        }
    }

    @Transactional
    public ItemResponse editItem(Long itemId, ItemSellerRequest request) {
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
            // Fetch existing images once and reuse
            imgs = uploadImages(request, item);

            item.setItemImages(imgs);
        } else {
            imgs = itemImagesRepository.findAllByItemIdOrderByIndexAsc(item.getId());
        }

        itemRepository.save(item);
        return itemSellerMapper.toItemResponse(item);
    }

    @Transactional
    public ItemImages replaceImage(Long imageId, Long itemId, ItemSellerRequest request) {
        User currentUser = SecurityUtils.getAuthenticatedUserOrThrow();

        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        if (!isOwner(currentUser, item)) {
            throw new AccessDeniedException("Access denied");
        }

        ItemImages foundImg = itemImagesRepository.findById(imageId)
            .orElseThrow(() -> new IllegalArgumentException("Image not found"));

        if (!itemId.equals(foundImg.getItem().getId())) {
            throw new IllegalArgumentException("Image does not belong to the specified item");
        }

        List<ItemSellerRequest.ItemImages> replacements = request.getImagesUrlList();
        if (replacements == null || replacements.size() != 1) {
            throw new IllegalArgumentException("Provide exactly one image to replace");
        }

        String newUrl = replacements.get(0).getUrl();
        if (newUrl == null || newUrl.isBlank()) {
            throw new IllegalArgumentException("Replacement image URL is required");
        }

        foundImg.setUrl(newUrl);
        return itemImagesRepository.save(foundImg);
    }

    @Transactional
    public List<ItemImages> removeImageById(Long imageId, Long itemId) {
        User currentUser = SecurityUtils.getAuthenticatedUserOrThrow();
        ItemImages foundImg = itemImagesRepository.findById(imageId)
            .orElseThrow(() -> new IllegalArgumentException("Image not found"));
        // Check ownership: the current user must be the owner of the item
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new IllegalArgumentException("Item not found"));
        if (!isOwner(currentUser, item)) {
            throw new AccessDeniedException("Access denied");
        }
        // Ensure the image belongs to the given item
        if (!foundImg.getItem().getId().equals(itemId)) {
            throw new IllegalArgumentException("Image does not belong to the specified item");
        }
        itemImagesRepository.delete(foundImg);

        return reArrangeImage(itemId);
    }

    private List<ItemImages> reArrangeImage(Long itemId){
        List<ItemImages> imgs = itemImagesRepository.findAllByItemIdOrderByIndexAsc(itemId);
            for (int i = 0; i < imgs.size(); i++) {
                ItemImages image = imgs.get(i);
                image.setIndex(i);
            }
            itemImagesRepository.saveAll(imgs);

        return imgs;
    }

    public Map<Long, String> getFirstImageIndexForAllItems(List <Long> itemId){
    List<Object[]> firstIndeximgs= itemImagesRepository.findFirstImageIndexByitemId(itemId);
        log.info("Found first image index for items {}", firstIndeximgs);
        if (itemId.isEmpty()) {
            log.info("No Images found for items {}", itemId);
            return null;
        }

        return firstIndeximgs.stream().collect(Collectors.toMap(
            row -> (Long) row[0], 
            row -> (String) row[1].toString()));

    }
    
    @Transactional
    public void swipeImageIndex(Long itemId, int fIndex, int sIndex) {
        if (fIndex < 0 || fIndex > 5 || sIndex < 0 || sIndex > 5) {
            throw new IllegalArgumentException("Image indexes must be between 0 and 5");
        }
        User currentUser = SecurityUtils.getAuthenticatedUserOrThrow();
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new IllegalArgumentException("Item not found"));
        if (!isOwner(currentUser, item)) {
            throw new AccessDeniedException("Access denied");
        }
        ItemImages fImg = itemImagesRepository.findImageByIndex(itemId, fIndex)
            .orElseThrow(() -> new IllegalArgumentException("Image not found"));
        ItemImages sImg = itemImagesRepository.findImageByIndex(itemId, sIndex)
            .orElseThrow(() -> new IllegalArgumentException("Image not found"));
        int swipe = fImg.getIndex();
        fImg.setIndex(sImg.getIndex());
        sImg.setIndex(swipe);
        itemImagesRepository.saveAll(Arrays.asList(fImg, sImg));
    }

    public boolean isOwner(User user, Item item){
        return user != null && item != null && user.getId().equals(item.getSeller().getId());
    }
}
