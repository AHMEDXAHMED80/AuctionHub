package com.example.auctionhub.auctionhub.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.auctionhub.auctionhub.models.Item;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * DTO representing an item for bidder responses.
 * Includes item details, current bid, seller info, and bid statistics.
 * Sensitive fields are excluded for security.
 */
public class ItemBidderResponse {
    /** Item ID */
    private Long id;
    /** Item title */
    private String title;
    /** Item description */
    private String description;
    /** Item category */
    private Item.ItemCategory category;
    /** Starting price */
    private BigDecimal startingPrice;
    /** Current highest bid */
    private BigDecimal currentHighestBid;
    /** Auction start date/time */
    private LocalDateTime startDate;
    /** Auction end date/time */
    private LocalDateTime endDate;
    /** Item status */
    private Item.ItemStatus status;
    /** Seller's user ID (minimal info) */
    private Long sellerId;
    /** Seller's username (minimal info) */
    private String sellerUsername;
    /** Total number of bids */
    private Long totalBids;
    // ...existing code...
}
