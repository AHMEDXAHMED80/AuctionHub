package com.example.auctionhub.auctionhub.events.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentLifecycleEvent(
    EventType eventType,         // PAYMENT_SUCCEEDED, PAYMENT_FAILED, PAYMENT_REFUNDED, ...
    Long paymentId,              // your DB payment id
    Long userId,                 // affected user
    BigDecimal amount,           // amount in major unit (e.g. 25.50)
    String currency,             // "usd"
    String stripePaymentIntentId,
    String stripeEventId,        // Stripe webhook event id
    String status,               // SUCCEEDED / FAILED / REFUNDED / CANCELED / PROCESSING
    String failureReason,        // null unless failed
    Instant occurredAt           // when event happened
) {
    public enum EventType {
        PAYMENT_SUCCEEDED,
        PAYMENT_FAILED,
        PAYMENT_PROCESSING,
        PAYMENT_CANCELED,
        PAYMENT_REFUNDED
    }
}
