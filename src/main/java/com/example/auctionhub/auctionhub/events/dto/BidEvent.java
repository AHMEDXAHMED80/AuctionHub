package com.example.auctionhub.auctionhub.events.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BidEvent {
    private Long itemId;
    private Long userId;
    private String username;
    private BigDecimal bidAmount;
    private LocalDateTime timestamp;
    private String itemName;
}
