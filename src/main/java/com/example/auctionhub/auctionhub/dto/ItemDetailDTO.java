package com.example.auctionhub.auctionhub.dto;

import com.example.auctionhub.auctionhub.models.Item.ItemCategory;
import com.example.auctionhub.auctionhub.models.Item.ItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class ItemDetailDTO {
    
    // Basic info
    private Long id;
    private String title;
    private String description;  // Full description
    private ItemCategory category;
    private ItemStatus status;
    
    // Price info
    private BigDecimal startingPrice;
    private BigDecimal currentHighestBid;
    
    // Time info
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Duration timeRemaining;
    
    // Seller info
    private Long sellerId;
    private String sellerName;
    private Double sellerRating;
    
    // Images
    private List<String> imageUrls;  // All images, not just thumbnail
    
    // Bid info
    private Integer bidCount;
    private List<BidResponse> recentBids;  // Last 5-10 bids
    
    // Current user context (optional)
    private Boolean hasUserBid;
    private Boolean isUserWatching;
    private Boolean isUserSeller;
}
