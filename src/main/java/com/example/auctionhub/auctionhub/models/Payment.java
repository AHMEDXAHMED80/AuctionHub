package com.example.auctionhub.auctionhub.models;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity representing a payment transaction in the auction system.
 * Includes Stripe integration fields and payment status tracking.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment {

    /**
     * Enum for payment status (INITIATED, PROCESSING, SUCCEEDED, FAILED, CANCELED, REFUNDED).
     */
    public enum PaymentStatus {
        INITIATED,
        PROCESSING,
        SUCCEEDED,
        FAILED,
        CANCELED,
        REFUNDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "stripe_client_secret")
    private String stripeClientSecret;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.INITIATED;
    
    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    public void createdAt() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void updatedAt() {
        updatedAt = Instant.now();
    }
}

