package com.example.auctionhub.auctionhub.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * DTO representing authentication response payloads.
 * Includes user ID, username, and a message.
 */
@Data
@NoArgsConstructor
public class AuthResponse {
    /** User ID */
    private Long userId;
    /** Username */
    private String username;
    /** Response message */
    private String message;

    public AuthResponse(Long userId, String username, String message) {
        this.userId = userId;
        this.username = username;
        this.message = message;
    }
}
