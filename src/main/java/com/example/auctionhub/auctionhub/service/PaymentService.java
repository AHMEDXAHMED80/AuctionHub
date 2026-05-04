package com.example.auctionhub.auctionhub.service;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.auctionhub.auctionhub.dto.PaymentDetailsResponse;
import com.example.auctionhub.auctionhub.mapper.PaymentMapper;
import com.example.auctionhub.auctionhub.models.Payment;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.repository.PaymentRepository;
import com.example.auctionhub.auctionhub.security.SecurityUtils;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for handling payment operations, including creation, retrieval, and Stripe integration.
 * Validates payment amounts and manages persistence and mapping of payment entities.
 */
@Slf4j
@Service
public class PaymentService {
    /**
     * Service for Stripe payment integration.
     */
    private final StripeService stripeService;

    /**
     * Repository for payment persistence operations.
     */
    private final PaymentRepository paymentRepository;

    /**
     * Mapper for converting Payment entities to response DTOs.
     */
    private final PaymentMapper paymentMapper;

    /**
     * Constructs a PaymentService with required dependencies.
     *
     * @param stripeService      Stripe integration service
     * @param paymentRepository  Payment repository for persistence
     * @param paymentMapper      Mapper for Payment to DTOs
     */
    public PaymentService(StripeService stripeService, PaymentRepository paymentRepository,
            PaymentMapper paymentMapper) {
        this.stripeService = stripeService;
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
    }

    /**
     * Creates a new payment and initiates Stripe PaymentIntent.
     *
     * @param amount Amount to pay (in dollars, e.g., 25.50)
     * @return Payment entity with clientSecret for frontend
     * @throws IllegalArgumentException if amount is invalid
     */
    @Transactional
    public Payment createPayment(BigDecimal amount) {
        User currentUser = SecurityUtils.getCurrentUser();
        log.info("Creating payment for user {} with amount: ${}", currentUser.getId(), amount);
        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Invalid payment amount for user {}: {}", currentUser.getId(), amount);
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (amount.compareTo(new BigDecimal("10000")) > 0) {
            log.warn("Payment amount exceeds limit for user {}: ${}", currentUser.getId(), amount);
            throw new IllegalArgumentException("Amount cannot exceed $10,000");
        }
        try {
            // Convert amount to cents for Stripe
            Long stripeAmount = stripeService.convertToStripeAmount(amount);

            // Create PaymentIntent with Stripe
            PaymentIntent paymentIntent = stripeService.createPaymentIntent(stripeAmount, "usd", currentUser.getId());

            // Create Payment record in database
            Payment payment = new Payment();
            payment.setUser(currentUser);
            payment.setAmount(amount);
            payment.setStripePaymentIntentId(paymentIntent.getId());
            payment.setStripeClientSecret(paymentIntent.getClientSecret());
            payment.setPaymentStatus(Payment.PaymentStatus.INITIATED);
            // Note: Wallet is credited via webhook when payment.succeeded event is received

            Payment savedPayment = paymentRepository.save(payment);
            log.info("Payment created successfully - ID: {}, PaymentIntent: {}, User: {}",
                    savedPayment.getId(), savedPayment.getStripePaymentIntentId(), currentUser.getId());
            return savedPayment;

        } catch (StripeException e) {
            log.error("Stripe error creating payment for user {}: {}", currentUser.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to create payment: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Payment refundPayment(Long paymentId, String reasonOfRefund) {

        User currentUser = SecurityUtils.getCurrentUser();
        log.info("Refund requested for payment {} by user {}", paymentId, currentUser.getId());

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        // Check ownership using .equals() instead of !=
        if (!currentUser.getId().equals(payment.getUser().getId())) {
            log.warn("Unauthorized refund attempt - User {} attempted to refund payment {} owned by user {}",
                    currentUser.getId(), paymentId, payment.getUser().getId());
            throw new RuntimeException("You are not authorized to refund this payment");
        }

        if (payment.getPaymentStatus() != Payment.PaymentStatus.SUCCEEDED) {
            throw new RuntimeException("Can't refund, status: " + payment.getPaymentStatus());
        }

        try {
            String paymentIntentId = payment.getStripePaymentIntentId();
            Long amount = stripeService.convertToStripeAmount(payment.getAmount());
            stripeService.createRefund(paymentIntentId, amount, reasonOfRefund);
            log.info("Refund initiated successfully for payment {}, amount: ${}, reason: {}",
                    paymentId, payment.getAmount(), reasonOfRefund);
            return payment;

        } catch (StripeException e) {
            log.error("Stripe error refunding payment {}: {}", paymentId, e.getMessage(), e);
            throw new RuntimeException("Failed to refund payment: " + e.getMessage(), e);
        }
    }

    /**
     * Cancel a pending payment
     * 
     * @param paymentId Payment ID to cancel
     * @return Canceled payment
     */
    @Transactional
    public Payment cancelPayment(Long paymentId) {
        User currentUser = SecurityUtils.getCurrentUser();
        log.info("Cancellation requested for payment {} by user {}", paymentId, currentUser.getId());

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        // Check ownership
        if (!currentUser.getId().equals(payment.getUser().getId())) {
            log.warn("Unauthorized cancel attempt - User {} attempted to cancel payment {} owned by user {}",
                    currentUser.getId(), paymentId, payment.getUser().getId());
            throw new RuntimeException("You are not authorized to cancel this payment");
        }

        // Check if payment can be canceled (only INITIATED or PROCESSING payments)
        if (payment.getPaymentStatus() != Payment.PaymentStatus.INITIATED
                && payment.getPaymentStatus() != Payment.PaymentStatus.PROCESSING) {
            throw new RuntimeException("Cannot cancel payment with status: " + payment.getPaymentStatus());
        }

        try {
            String paymentIntentId = payment.getStripePaymentIntentId();
            stripeService.cancelPaymentIntent(paymentIntentId);
            log.info("Payment {} canceled successfully for user {}", paymentId, currentUser.getId());

            // Webhook will update status to CANCELED
            return payment;

        } catch (StripeException e) {
            log.error("Stripe error canceling payment {}: {}", paymentId, e.getMessage(), e);
            throw new RuntimeException("Failed to cancel payment: " + e.getMessage(), e);
        }
    }

    /**
     * Get payment history for current user
     * 
     * @return List of payments ordered by created date (newest first)
     */
    public Page<PaymentDetailsResponse> getPaymentHistory(Pageable pageable) {
        User currentUser = SecurityUtils.getCurrentUser();
        Page<Payment> payments = paymentRepository
                .findByUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable);
        return payments.map(paymentMapper::toPaymentDetailsResponse);
    }

    /**
     * Get a specific payment by ID
     * 
     * @param paymentId Payment ID
     * @return Payment details
     */
    public Payment getPaymentById(Long paymentId) {
        User currentUser = SecurityUtils.getCurrentUser();
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        // Check ownership
        if (!currentUser.getId().equals(payment.getUser().getId())) {
            throw new RuntimeException("You are not authorized to view this payment");
        }

        return payment;
    }
}
