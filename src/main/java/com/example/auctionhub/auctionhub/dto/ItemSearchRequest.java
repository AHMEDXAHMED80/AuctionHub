package com.example.auctionhub.auctionhub.dto;

import com.example.auctionhub.auctionhub.models.Item.ItemCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemSearchRequest {

    /**
     * Enum for sort options
     */
    public enum ItemSortOption {
        PRICE_LOW,      // Cheapest first
        PRICE_HIGH,     // Most expensive first
        ENDING_SOON,    // Ending soonest first
        NEWEST          // Recently added first
    }

    // 1. Search keyword (searches in title + description)
    private String keyword;

    // 2. Category filter
    private ItemCategory category;

    // 3. Price range (based on CURRENT BID, not starting price)
    private BigDecimal minPrice; 
    private BigDecimal maxPrice;

    // 4. End time filters
    private Boolean endingSoon;      // Ending within 24 hours
    private Boolean endingToday;     // Ending today
    private Integer endingInHours;   // Custom hours (e.g., 6, 12, 48)

    // Sorting & Pagination
    private ItemSortOption sortBy;   // Type-safe sorting options
    private Integer page;
    private Integer size;
}
