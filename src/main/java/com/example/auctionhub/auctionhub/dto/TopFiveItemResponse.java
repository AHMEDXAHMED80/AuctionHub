package com.example.auctionhub.auctionhub.dto;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.auctionhub.auctionhub.models.Item;

import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopFiveItemResponse {
    /** Item ID */
    private Long id;
    /** Item title */
    private String title;
    /** List of image URLs */
    private String firstIndexUrlImage;
    /** Item category */
    private Item.ItemCategory category;
    /** Current price */
    private BigDecimal currentPrice;
    /** Auction end date/time */
    private LocalDateTime endDate;

    private int rank;

}
