package com.example.auctionhub.auctionhub.controller;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.auctionhub.auctionhub.dto.BidResponse;
import com.example.auctionhub.auctionhub.dto.ItemBidderResponse;
import com.example.auctionhub.auctionhub.dto.ListResponse;
import com.example.auctionhub.auctionhub.dto.UserBidHistoryResponse;
import com.example.auctionhub.auctionhub.service.BidService;

import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for bid operations.
 * Handles bidding, bid history, and bid status queries.
 */
@Slf4j
@RestController
@RequestMapping("/api/bids")
public class BidController {

    private final BidService bidService;

    public BidController(BidService bidService) {
        this.bidService = bidService;
    }

    /**
     * Place a bid on an item
     * POST /api/bids
     */
    @PostMapping
    public ResponseEntity<String> placeBid(@RequestBody PlaceBidRequest request) {
        log.info("Placing bid on item {} with amount {}", request.itemId(), request.bidAmount());
        bidService.placeBid(request.itemId(), request.bidAmount());
        return ResponseEntity.ok("Bid placed successfully");
    }

    /**
     * Get bid history for a specific item
     * GET /api/bids/item/{itemId}
     */
    @GetMapping("/item/{itemId}")
    public ResponseEntity<ListResponse<BidResponse>> getBidHistory(
            @PathVariable Long itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Fetching bid history for item {}", itemId);
        Page<BidResponse> bids = bidService.getBidhistory(itemId, PageRequest.of(page, size));
        return ResponseEntity.ok(new ListResponse<>(bids));
    }

    /**
     * Get all bids placed by the current user
     * GET /api/bids/my-bids
     */
    @GetMapping("/my-bids")
    public ResponseEntity<ListResponse<BidResponse>> getUserBids(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Fetching bids for current user");
        Page<BidResponse> bids = bidService.getUserBids(PageRequest.of(page, size));
        return ResponseEntity.ok(new ListResponse<>(bids));
    }

    /**
     * Get current highest bid for an item
     * GET /api/bids/item/{itemId}/highest
     */
    @GetMapping("/item/{itemId}/highest")
    public ResponseEntity<HighestBidResponse> getHighestBid(@PathVariable Long itemId) {
        log.info("Fetching highest bid for item {}", itemId);
        BigDecimal highestBid = bidService.getHighestBid(itemId);
        return ResponseEntity.ok(new HighestBidResponse(itemId, highestBid));
    }

    /**
     * Check if current user is winning the auction
     * GET /api/bids/item/{itemId}/am-i-winning
     */
    @GetMapping("/item/{itemId}/am-i-winning")
    public ResponseEntity<WinningStatusResponse> isUserWinning(@PathVariable Long itemId) {
        log.info("Checking if user is winning item {}", itemId);
        // Note: userId should be extracted from authentication context in the service
        Long userId = null; // Will be extracted from SecurityContext in service
        boolean isWinning = bidService.isUserWinning(itemId, userId);
        return ResponseEntity.ok(new WinningStatusResponse(itemId, isWinning));
    }

    /**
     * Get username of current highest bidder
     * GET /api/bids/item/{itemId}/winner
     */
    @GetMapping("/item/{itemId}/winner")
    public ResponseEntity<CurrentWinnerResponse> getWhoIsWinning(@PathVariable Long itemId) {
        log.info("Fetching current winner for item {}", itemId);
        String winner = bidService.WhoisWinning(itemId);
        return ResponseEntity.ok(new CurrentWinnerResponse(itemId, winner));
    }

    /**
     * Get user's active bids (items still in auction)
     * GET /api/bids/active
     */
    @GetMapping("/active")
    public ResponseEntity<ListResponse<ItemBidderResponse>> getUserActiveBids(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Fetching active bids for current user");
        Page<ItemBidderResponse> activeBids = bidService.getUserActiveBids(PageRequest.of(page, size));
        return ResponseEntity.ok(new ListResponse<>(activeBids));
    }

    /**
     * Get user's inactive bids (auctions ended)
     * GET /api/bids/history
     */
    @GetMapping("/history")
    public ResponseEntity<ListResponse<UserBidHistoryResponse>> getUserInactiveBids(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Fetching bid history for current user");
        Page<UserBidHistoryResponse> history = bidService.getUserunActiveBids(PageRequest.of(page, size));
        return ResponseEntity.ok(new ListResponse<>(history));
    }

    // Request/Response DTOs
    public record PlaceBidRequest(Long itemId, BigDecimal bidAmount) {}
    public record HighestBidResponse(Long itemId, BigDecimal highestBid) {}
    public record WinningStatusResponse(Long itemId, boolean isWinning) {}
    public record CurrentWinnerResponse(Long itemId, String winnerUsername) {}
}
