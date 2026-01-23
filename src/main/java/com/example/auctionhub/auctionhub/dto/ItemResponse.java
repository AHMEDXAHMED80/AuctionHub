package com.example.auctionhub.auctionhub.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.example.auctionhub.auctionhub.models.Item;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * DTO representing an item for seller responses.
 * Includes item details, images, price, and seller info.
 */
public class ItemResponse {
    /** Item ID */
    private Long id;
    /** Item title */
    private String title;
    /** Item description */
    private String description;
    /** List of image URLs */
    private List<String> imagesUrlList;
    /** Item category */
    private Item.ItemCategory category;
    /** Starting price */
    private BigDecimal startingPrice;
    /** Current price */
    private BigDecimal currentPrice;
    /** Auction start date/time */
    private LocalDateTime startDate;
    /** Auction end date/time */
    private LocalDateTime endDate;
    /** Seller's username */
    private String sellerUsername;
    /** Whether the item is active */
    private Boolean isActive;
}
