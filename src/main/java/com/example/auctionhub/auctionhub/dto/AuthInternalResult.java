package com.example.auctionhub.auctionhub.dto;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
public class AuthInternalResult {
    private Long userId;
    private String username;
    private String accessToken;
    private String refreshToken;

    public AuthInternalResult(Long userId, String username, String accessToken, String refreshToken) {
        this.userId = userId;
        this.username = username;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

}
