package com.example.auctionhub.auctionhub.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.auctionhub.auctionhub.models.Payment.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentResponse {
    
    private Long id;
    private BigDecimal amount;
    private PaymentStatus status;
    private String stripePaymentIntentId;
    private String stripeClientSecret;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;
}
