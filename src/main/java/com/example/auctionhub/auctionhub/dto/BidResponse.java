package com.example.auctionhub.auctionhub.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * DTO representing a bid for response payloads.
 * Includes bidder username, item name, bid amount, and creation time.
 */
public class BidResponse {
    /** Username of the bidder */
    private String username;
    /** Name of the item bid on */
    private String itemName;
    /** Amount of the bid */
    private BigDecimal bidAmount;
    /** Time the bid was placed */
    private LocalDateTime createdAt;
}
