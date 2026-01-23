package com.example.auctionhub.auctionhub.dto;

import java.time.LocalDateTime;

import com.example.auctionhub.auctionhub.models.Bid;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserBidHistoryResponse {
    private Long itemId;
    private String Title;
    private String defualtimageUrl;
    private String LastBidAmount;
    private Bid.bidStatus status;
    private LocalDateTime endedAt;
}
