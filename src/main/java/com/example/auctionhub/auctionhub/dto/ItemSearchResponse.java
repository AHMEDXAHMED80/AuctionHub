package com.example.auctionhub.auctionhub.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemSearchResponse {
    private List<ItemSearchResultDTO> items;  // ← List of items!
    
    // Pagination
    private int currentPage;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
    
    // Optional: applied filters
    private String appliedKeyword;
    private String appliedCategory;
}
