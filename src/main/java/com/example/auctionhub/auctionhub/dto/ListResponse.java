package com.example.auctionhub.auctionhub.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Generic wrapper for paginated collection API responses.
 */
public class ListResponse<T> {

    private List<T> data;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public ListResponse(Page<T> pageResult) {
        this.data = pageResult.getContent();
        this.page = pageResult.getNumber();
        this.size = pageResult.getSize();
        this.totalElements = pageResult.getTotalElements();
        this.totalPages = pageResult.getTotalPages();
    }

    public List<T> getData() { return data; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
}
