package com.example.auctionhub.auctionhub.dto;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TokenPairResponse {
    private String accessToken;
    private String refreshToken;

    public TokenPairResponse(String accessToken, String refreshToken){
        this.accessToken =accessToken;
        this.refreshToken=refreshToken;
    }
}
