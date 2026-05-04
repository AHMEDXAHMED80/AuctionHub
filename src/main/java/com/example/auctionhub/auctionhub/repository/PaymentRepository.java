package com.example.auctionhub.auctionhub.repository;

import com.example.auctionhub.auctionhub.models.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    /**
     * Find payment by Stripe PaymentIntent ID
     */
    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);
    
    /**
     * Find all payments for a specific user, ordered by creation date descending
     */
    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);
    Page<Payment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    /**
     * Find all payments by status
     */
    List<Payment> findByPaymentStatus(Payment.PaymentStatus status);
}
