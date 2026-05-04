package com.example.auctionhub.auctionhub.dto;

public class NotificationResponseDto {
    private Long id;
    private String message;
    private String type;
    private boolean isRead;

    public NotificationResponseDto() {}

    public NotificationResponseDto(Long id, String message, String type, boolean isRead) {
        this.id = id;
        this.message = message;
        this.type = type;
        this.isRead = isRead;
        
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

}
