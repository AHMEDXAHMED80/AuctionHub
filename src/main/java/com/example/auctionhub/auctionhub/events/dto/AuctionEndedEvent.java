package com.example.auctionhub.auctionhub.events.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.auctionhub.auctionhub.models.Item.ItemStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuctionEndedEvent {
    private Long itemId;
    private String itemName;
    private Long sellerId;
    private BigDecimal finalBidAmount;
    private Long winningBidderId;
    private String winningBidderUsername;
    private ItemStatus auctionStatus;
    private LocalDateTime endedAt;
    private boolean hasBids;
}
