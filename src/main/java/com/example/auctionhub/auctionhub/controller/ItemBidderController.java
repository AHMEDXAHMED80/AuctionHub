package com.example.auctionhub.auctionhub.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auctionhub.auctionhub.dto.ItemDetailDTO;
import com.example.auctionhub.auctionhub.dto.ItemSearchRequest;
import com.example.auctionhub.auctionhub.dto.ItemSearchResponse;
import com.example.auctionhub.auctionhub.dto.TopFiveItemResponse;
import com.example.auctionhub.auctionhub.service.ItemBidderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for bidder-facing item discovery APIs.
 * Handles search, item detail, and top-viewed item listing.
 */
@Slf4j
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemBidderController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ItemBidderService itemBidderService;

    /**
     * Returns top viewed items.
     */
    @GetMapping("/top-viewed")
    public ResponseEntity<List<TopFiveItemResponse>> getTopViewedItems() {
        List<TopFiveItemResponse> items = itemBidderService.getTopFiveItems();
        return ResponseEntity.ok(items);
    }

    /**
     * Searches active items with filters/sorting/pagination.
     * Pagination is normalized to prevent invalid or huge page sizes.
     */
    @PostMapping("/search")
    public ResponseEntity<ItemSearchResponse> searchItems(@RequestBody(required = false) ItemSearchRequest request) {
        ItemSearchRequest safeRequest = normalizePagination(request);
        log.info("Searching items with page={} size={} sort={}",
                safeRequest.getPage(), safeRequest.getSize(), safeRequest.getSortBy());
        ItemSearchResponse response = itemBidderService.searchItems(safeRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns full detail payload for a single item page.
     */
    @GetMapping("/{itemId}/detail")
    public ResponseEntity<ItemDetailDTO> getItemDetail(@PathVariable Long itemId) {
        ItemDetailDTO detail = itemBidderService.getItemDetail(itemId);
        return ResponseEntity.ok(detail);
    }

    private ItemSearchRequest normalizePagination(ItemSearchRequest request) {
        ItemSearchRequest safe = request != null ? request : new ItemSearchRequest();

        Integer page = safe.getPage();
        if (page == null || page < 0) {
            safe.setPage(DEFAULT_PAGE);
        }

        Integer size = safe.getSize();
        if (size == null || size <= 0) {
            safe.setSize(DEFAULT_SIZE);
        } else if (size > MAX_SIZE) {
            safe.setSize(MAX_SIZE);
        }

        return safe;
    }
}
