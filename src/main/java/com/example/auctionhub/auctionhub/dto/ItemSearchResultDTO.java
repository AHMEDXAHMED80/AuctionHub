package com.example.auctionhub.auctionhub.dto;

import java.math.BigDecimal;
import java.time.Duration;
import com.example.auctionhub.auctionhub.models.Item;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemSearchResultDTO {
        /** Item ID */
    private Long id;
    /** Item title */
    private String title;
    /** List of image URLs */
    private String imagesUrlList;
    /** Item category */
    private Item.ItemCategory category;
    /** Current price */
    private BigDecimal currentPrice;
    /** Auction end date/time */
    private Duration timeRemaining;
}
