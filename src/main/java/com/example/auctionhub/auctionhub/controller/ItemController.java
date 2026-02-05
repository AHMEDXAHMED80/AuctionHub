package com.example.auctionhub.auctionhub.controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.auctionhub.auctionhub.constants.ApiConstants;
import com.example.auctionhub.auctionhub.dto.ItemResponse;
import com.example.auctionhub.auctionhub.models.ItemImages;
import com.example.auctionhub.auctionhub.dto.ItemSellerRequest;
import com.example.auctionhub.auctionhub.service.ItemsSellerService;
import com.example.auctionhub.auctionhub.service.ItemImageService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;


/**
 * Controller for handling item endpoints, including creation, editing, and retrieval for sellers.
 * Delegates business logic to ItemsSellerService and ItemImageService.
 */
@RestController
@RequestMapping(ApiConstants.ITEM_BASE)
public class ItemController {

    private final ItemsSellerService itemsService;
    private final ItemImageService itemImageService;

    /**
     * Constructs an ItemController with required dependencies.
     *
     * @param itemsService Service for item business logic
     * @param itemImageService Service for item image operations
     */
    public ItemController(ItemsSellerService itemsService, ItemImageService itemImageService) {
        this.itemsService = itemsService;
        this.itemImageService = itemImageService;
    }

        // GET /api/items/user - get all items listed by the current user
    /**
     * Retrieves all items listed by the current authenticated user (seller).
     *
     * @return List of ItemResponse DTOs for the user's items
     */
    @org.springframework.web.bind.annotation.GetMapping("/user")
    public ResponseEntity<List<ItemResponse>> getAllItemsForCurrentUser() {
        List<ItemResponse> items = itemsService.getAllItemsForCurrentUser();
        return ResponseEntity.ok(items);
    }

    /**
     * Creates a new item for the authenticated seller.
     *
     * @param request ItemSellerRequest with item details
     * @return ItemResponse DTO for the created item
     */
    @PostMapping // POST /api/items
    public ResponseEntity<ItemResponse> createItem(@Valid @RequestBody ItemSellerRequest request) {
        ItemResponse item = itemsService.createItem(request);
        URI location = URI.create(ApiConstants.ITEM_BASE + "/" + item.getId());
        return ResponseEntity.created(location).body(item);
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<ItemResponse> editItem(@PathVariable Long itemId, @Valid @RequestBody ItemSellerRequest request) {
        ItemResponse editedItem = itemsService.editItem(itemId, request);
        return ResponseEntity.ok(editedItem);
    }

    @PutMapping("/{itemId}/images/{imageId}")
    public ResponseEntity<ItemImages> replaceImage(
            @PathVariable Long itemId,
            @PathVariable Long imageId,
            @Valid @RequestBody ItemSellerRequest request) {

        ItemImages updatedImage = itemImageService.replaceImage(imageId, itemId, request);
        return ResponseEntity.ok(updatedImage);
    }

    @DeleteMapping("/{itemId}/images/{imageId}")
    public ResponseEntity<List<ItemImages>> removeImage(
            @PathVariable Long itemId,
            @PathVariable Long imageId
    ) {

        List<ItemImages> DeletedImage = itemImageService.removeImageById(imageId, itemId);
        return ResponseEntity.ok(DeletedImage);
    }


    @PostMapping("/{itemId}/images/swap")
    public ResponseEntity<Void> swapImageIndex(@PathVariable Long itemId,
                                            @RequestParam int fIndex,
                                            @RequestParam int sIndex) {
        itemImageService.swipeImageIndex(itemId, fIndex, sIndex);
        return ResponseEntity.noContent().build();
    }



}
