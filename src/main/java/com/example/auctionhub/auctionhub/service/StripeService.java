package com.example.auctionhub.auctionhub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.auctionhub.auctionhub.events.dto.PaymentLifecycleEvent;
import com.example.auctionhub.auctionhub.events.producer.PaymentLifecycleProducer;
import com.example.auctionhub.auctionhub.models.Payment;
import com.example.auctionhub.auctionhub.repository.PaymentRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Service for integrating with Stripe payment APIs.
 * Handles creation, retrieval, cancellation of payment intents, refunds,
 * and processes Stripe webhook events to update payment status and user wallet.
 *
 * <p><b>Wallets are only credited after Stripe confirms payment success via webhook.</b>
 * This prevents crediting wallets for payments that fail or are abandoned.</p>
 *
 * <p>All Stripe event handlers are private and invoked only by handleStripeEvent().</p>
 */
@Slf4j
@Service
public class StripeService {

    @Value("${STRIPE_SECRET_KEY}")
    private String stripeSecretKey;
    
    @Value("${stripe.currency:usd}")
    private String defaultCurrency;
    
    private final PaymentRepository paymentRepository;
    private final WalletService walletService;
    private final PaymentLifecycleProducer paymentLifecycleProducer;
    
    public StripeService(
            PaymentRepository paymentRepository,
            WalletService walletService,
            PaymentLifecycleProducer paymentLifecycleProducer) {
        this.paymentRepository = paymentRepository;
        this.walletService = walletService;
        this.paymentLifecycleProducer = paymentLifecycleProducer;
    }
    
    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }
    

    /**
     * Creates a Stripe PaymentIntent for wallet top-up.
     *
     * @param amount Amount in smallest currency unit (e.g., cents)
     * @param currency Currency code (e.g., "usd")
     * @param userId User ID for tracking
     * @return PaymentIntent with client secret
     * @throws StripeException if Stripe API call fails
     */
    public PaymentIntent createPaymentIntent(Long amount, String currency, Long userId) throws StripeException {
        // Validate input
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(amount)
            .setCurrency(currency != null ? currency : defaultCurrency)
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                    .setEnabled(true)
                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                    .build()
            )
            .putMetadata("userId", userId.toString())
            .putMetadata("type", "WALLET_TOPUP")
            .putMetadata("timestamp", String.valueOf(System.currentTimeMillis()))
            .setDescription("Wallet top-up for user " + userId)
            .build();

        return PaymentIntent.create(params);
    }

    /**
     * Retrieves a payment intent by ID to check its status.
     *
     * @param paymentIntentId Stripe PaymentIntent ID
     * @return PaymentIntent with current status
     * @throws StripeException if Stripe API call fails
     */
    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            throw new IllegalArgumentException("Payment Intent ID cannot be null or empty");
        }
        return PaymentIntent.retrieve(paymentIntentId);
    }
    
    /**
     * Cancels a payment intent before it's confirmed.
     *
     * @param paymentIntentId Stripe PaymentIntent ID
     * @return Cancelled PaymentIntent
     * @throws StripeException if Stripe API call fails
     */
    public PaymentIntent cancelPaymentIntent(String paymentIntentId) throws StripeException {
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            throw new IllegalArgumentException("Payment Intent ID cannot be null or empty");
        }
        
        PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
        
        PaymentIntentCancelParams params = PaymentIntentCancelParams.builder()
            .setCancellationReason(PaymentIntentCancelParams.CancellationReason.REQUESTED_BY_CUSTOMER)
            .build();
            
        return paymentIntent.cancel(params);
    }
    
    /**
     * Creates a refund for a successful payment.
     *
     * @param paymentIntentId Stripe PaymentIntent ID to refund
     * @param amount Amount to refund in smallest currency unit (null for full refund)
     * @param reason Refund reason
     * @return Refund object
     * @throws StripeException if Stripe API call fails
     */
    public Refund createRefund(String paymentIntentId, Long amount, String reason) throws StripeException {
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            throw new IllegalArgumentException("Payment Intent ID cannot be null or empty");
        }
        
        RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder()
            .setPaymentIntent(paymentIntentId);
            
        if (amount != null && amount > 0) {
            paramsBuilder.setAmount(amount);
        }
        
        if (reason != null && !reason.isEmpty()) {
            try {
                paramsBuilder.setReason(RefundCreateParams.Reason.valueOf(reason.toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Unrecognised reason string — fall back to the default
                paramsBuilder.setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER);
            }
        }
        
        return Refund.create(paramsBuilder.build());
    }
    
    /**
     * Converts BigDecimal amount to Stripe-compatible long (smallest currency unit).
     *
     * @param amount Amount in standard units (e.g., 10.50 USD)
     * @return Amount in cents (e.g., 1050)
     */
    public Long convertToStripeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        return amount.multiply(new BigDecimal("100")).longValue();
    }
    
    /**
     * Converts Stripe amount (smallest unit) back to BigDecimal.
     *
     * @param stripeAmount Amount in cents
     * @return Amount in standard units
     */
    public BigDecimal convertFromStripeAmount(Long stripeAmount) {
        if (stripeAmount == null) {
            throw new IllegalArgumentException("Stripe amount cannot be null");
        }
        return new BigDecimal(stripeAmount).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /**
     * Handles Stripe webhook events and dispatches to the appropriate handler.
     *
     * @param event Stripe Event object
     */
    public void handleStripeEvent(com.stripe.model.Event event) {
        switch (event.getType()) {
            case "payment_intent.succeeded":
                handlePaymentSucceeded(event);
                break;
                
            case "payment_intent.payment_failed":
                handlePaymentFailed(event);
                break;
                
            case "payment_intent.canceled":
                handlePaymentCanceled(event);
                break;
                
            case "payment_intent.processing":
                handlePaymentProcessing(event);
                break;
                
            case "charge.refunded":
                handleChargeRefunded(event);
                break;
                
            case "charge.refund.updated":
                handleRefundUpdated(event);
                break;
                
            default:
                log.warn("⚠️ Unhandled webhook event type: {}", event.getType());
                break;
        }
    }

    /**
     * Handles successful payment webhook - credits user wallet.
     *
     * <b>This is the authoritative source for wallet crediting.</b>
     * The wallet is ONLY credited after Stripe confirms payment success via webhook,
     * never during payment creation. This prevents crediting wallets for payments that fail or are abandoned.
     *
     * @param event Stripe webhook event containing payment details
     */
    private void handlePaymentSucceeded(com.stripe.model.Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getData().getObject();
        
        // Extract payment details from Stripe metadata
        String userId = paymentIntent.getMetadata().get("userId");
        Long amountInCents = paymentIntent.getAmount();
        BigDecimal amount = convertFromStripeAmount(amountInCents);
        String paymentIntentId = paymentIntent.getId();
        String currency = paymentIntent.getCurrency();

        if (userId == null || userId.isBlank()) {
            log.error("❌ Missing userId metadata in PaymentIntent {}", paymentIntentId);
            return;
        }

        long parsedUserId;
        try {
            parsedUserId = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            log.error("❌ Invalid userId metadata '{}' in PaymentIntent {}", userId, paymentIntentId);
            return;
        }

        log.info("✅ Payment succeeded for user {}, Amount: {} {}, PaymentIntent: {}",
                userId, amount, currency.toUpperCase(), paymentIntentId);

        try {
            Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new RuntimeException("Payment not found for intent: " + paymentIntentId));

            if (payment.getPaymentStatus() == Payment.PaymentStatus.SUCCEEDED) {
                log.info("Skipping duplicate payment_intent.succeeded for PaymentIntent {}", paymentIntentId);
                return;
            }

            // Credit wallet BEFORE marking SUCCEEDED so that if crediting fails the
            // payment stays in its previous state and the event can be replayed.
            walletService.creditBalance(parsedUserId, amount);

            payment.setPaymentStatus(Payment.PaymentStatus.SUCCEEDED);
            paymentRepository.save(payment);
            
            log.info("✅ Wallet credited successfully for user {}", parsedUserId);

            publishPaymentEvent(
                    payment,
                    PaymentLifecycleEvent.EventType.PAYMENT_SUCCEEDED,
                    event.getId(),
                    currency,
                    null
            );
            
            // TODO: Send confirmation email/notification
            // notificationService.sendPaymentConfirmation(Long.parseLong(userId), amount);
        } catch (Exception e) {
            log.error("❌ Error processing payment success for PaymentIntent {}: {}", paymentIntentId, e.getMessage(), e);
        }
    }

    /**
     * Handles failed payment webhook - marks payment as failed.
     * Captures failure details for debugging and user notification.
     * No wallet operation occurs on payment failure.
     *
     * @param event Stripe webhook event containing failure details
     */
    private void handlePaymentFailed(com.stripe.model.Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getData().getObject();
        
        String userId = paymentIntent.getMetadata().get("userId");
        String paymentIntentId = paymentIntent.getId();
        // Extract error details for logging and user notification
        String failureMessage = paymentIntent.getLastPaymentError() != null ? 
                               paymentIntent.getLastPaymentError().getMessage() : "Unknown error";
        String failureCode = paymentIntent.getLastPaymentError() != null ?
                            paymentIntent.getLastPaymentError().getCode() : null;
        
        log.warn("❌ Payment failed for user {}, Reason: {}, Code: {}, PaymentIntent: {}", 
                userId, failureMessage, failureCode, paymentIntentId);
        
        try {
            Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new RuntimeException("Payment not found for intent: " + paymentIntentId));
            
            payment.setPaymentStatus(Payment.PaymentStatus.FAILED);
            payment.setFailureReason(failureMessage);
            paymentRepository.save(payment);
            
            log.info("✅ Payment {} marked as failed in database", paymentIntentId);

            publishPaymentEvent(
                    payment,
                    PaymentLifecycleEvent.EventType.PAYMENT_FAILED,
                    event.getId(),
                    paymentIntent.getCurrency(),
                    failureMessage
            );
            
            // TODO: Notify user about failed payment
            // notificationService.sendPaymentFailedNotification(Long.parseLong(userId), failureMessage);
        } catch (Exception e) {
            log.error("❌ Error processing payment failure for PaymentIntent {}: {}", paymentIntentId, e.getMessage(), e);
        }
    }

    /**
     * Handles canceled payment - marks payment as canceled.
     *
     * @param event Stripe webhook event containing cancellation details
     */
    private void handlePaymentCanceled(com.stripe.model.Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getData().getObject();
        
        String userId = paymentIntent.getMetadata().get("userId");
        String paymentIntentId = paymentIntent.getId();
        String cancellationReason = paymentIntent.getCancellationReason();
        
        log.info("🚫 Payment canceled for user {}, Reason: {}, PaymentIntent: {}", 
                userId, cancellationReason, paymentIntentId);
        
        try {
            Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new RuntimeException("Payment not found for intent: " + paymentIntentId));
            
            payment.setPaymentStatus(Payment.PaymentStatus.CANCELED);
            payment.setFailureReason(cancellationReason);
            paymentRepository.save(payment);
            
            log.info("✅ Payment {} marked as canceled in database", paymentIntentId);

            publishPaymentEvent(
                    payment,
                    PaymentLifecycleEvent.EventType.PAYMENT_CANCELED,
                    event.getId(),
                    paymentIntent.getCurrency(),
                    cancellationReason
            );
        } catch (Exception e) {
            log.error("❌ Error processing payment cancellation for PaymentIntent {}: {}", paymentIntentId, e.getMessage(), e);
        }
    }

    /**
     * Handles payment in processing state.
     *
     * @param event Stripe webhook event
     */
    private void handlePaymentProcessing(com.stripe.model.Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getData().getObject();
        
        String userId = paymentIntent.getMetadata().get("userId");
        String paymentIntentId = paymentIntent.getId();
        
        log.info("⏳ Payment processing for user {}, PaymentIntent: {}", userId, paymentIntentId);
        
        try {
            Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new RuntimeException("Payment not found for intent: " + paymentIntentId));
            
            payment.setPaymentStatus(Payment.PaymentStatus.PROCESSING);
            paymentRepository.save(payment);
            
            log.info("✅ Payment {} marked as processing in database", paymentIntentId);

            publishPaymentEvent(
                    payment,
                    PaymentLifecycleEvent.EventType.PAYMENT_PROCESSING,
                    event.getId(),
                    paymentIntent.getCurrency(),
                    null
            );
        } catch (Exception e) {
            log.error("❌ Error updating payment to processing for PaymentIntent {}: {}", paymentIntentId, e.getMessage(), e);
        }
    }

    /**
     * Handles refund completion webhook - deducts from wallet.
     * When a payment is refunded, this reverses the wallet credit.
     * Only deducts if the original payment was successful to prevent double-deduction.
     *
     * @param event Stripe webhook event containing refund details
     */
    private void handleChargeRefunded(com.stripe.model.Event event) {
        com.stripe.model.Charge charge = (com.stripe.model.Charge) event.getData().getObject();
        
        String paymentIntentId = charge.getPaymentIntent();
        Long refundAmountInCents = charge.getAmountRefunded();
        BigDecimal refundAmount = convertFromStripeAmount(refundAmountInCents);
        String currency = charge.getCurrency();
        
        log.info("💰 Refund processed for PaymentIntent: {}, Refund Amount: {} {}", 
                paymentIntentId, refundAmount, currency.toUpperCase());
        
        try {
            Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new RuntimeException("Payment not found for intent: " + paymentIntentId));

            if (payment.getPaymentStatus() == Payment.PaymentStatus.REFUNDED) {
                log.info("Skipping duplicate charge.refunded for PaymentIntent {}", paymentIntentId);
                return;
            }
            
            // Critical: Only deduct if payment was previously SUCCEEDED
            // This prevents deducting from wallet for failed/canceled payments that were refunded
            if (payment.getPaymentStatus() == Payment.PaymentStatus.SUCCEEDED) {
                walletService.deductBalance(payment.getUser().getId(), refundAmount);
                log.info("✅ Wallet debited for refund - PaymentIntent: {}", paymentIntentId);
            }
            
            payment.setPaymentStatus(Payment.PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            
            log.info("✅ Payment {} marked as refunded in database", paymentIntentId);

            publishPaymentEvent(
                    payment,
                    PaymentLifecycleEvent.EventType.PAYMENT_REFUNDED,
                    event.getId(),
                    currency,
                    null
            );
            
            // TODO: Notify user about refund
            // notificationService.sendRefundConfirmation(payment.getUser().getId(), refundAmount);
        } catch (Exception e) {
            log.error("❌ Error processing refund for PaymentIntent {}: {}", paymentIntentId, e.getMessage(), e);
        }
    }

    /**
     * Handles refund status updates.
     *
     * @param event Stripe webhook event containing refund update details
     */
    private void handleRefundUpdated(com.stripe.model.Event event) {
        com.stripe.model.Refund refund = (com.stripe.model.Refund) event.getData().getObject();
        
        String refundId = refund.getId();
        String status = refund.getStatus();
        String paymentIntentId = refund.getPaymentIntent();
        Long refundAmountInCents = refund.getAmount();
        BigDecimal refundAmount = convertFromStripeAmount(refundAmountInCents);
        
        log.info("🔄 Refund updated: {}, Status: {}, PaymentIntent: {}, Amount: {}", 
                refundId, status, paymentIntentId, refundAmount);
        
        try {
            Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new RuntimeException("Payment not found for intent: " + paymentIntentId));
            
            if ("succeeded".equals(status)) {
                // Refund completed successfully - ensure payment is marked as REFUNDED
                if (payment.getPaymentStatus() != Payment.PaymentStatus.REFUNDED) {
                    payment.setPaymentStatus(Payment.PaymentStatus.REFUNDED);
                    paymentRepository.save(payment);
                }
                log.info("✅ Refund succeeded for payment: {}", paymentIntentId);
                
            } else if ("failed".equals(status)) {
                // Refund failed - log and potentially alert admin
                String failureReason = refund.getFailureReason();
                log.error("❌ Refund failed for payment: {}, Reason: {}", paymentIntentId, failureReason);
                
                // TODO: Alert admin about failed refund
                // notificationService.alertAdminRefundFailed(paymentIntentId, failureReason);
                
            } else if ("pending".equals(status)) {
                log.info("⏳ Refund pending for payment: {}", paymentIntentId);
            } else if ("canceled".equals(status)) {
                log.info("🚫 Refund canceled for payment: {}", paymentIntentId);
            }
        } catch (Exception e) {
            log.error("❌ Error processing refund update for PaymentIntent {}: {}", paymentIntentId, e.getMessage(), e);
        }
    }

    private void publishPaymentEvent(
            Payment payment,
            PaymentLifecycleEvent.EventType eventType,
            String stripeEventId,
            String currency,
            String failureReason) {
        try {
            String resolvedCurrency = currency != null ? currency.toLowerCase() : defaultCurrency;
            PaymentLifecycleEvent paymentEvent = new PaymentLifecycleEvent(
                    eventType,
                    payment.getId(),
                    payment.getUser().getId(),
                    payment.getAmount(),
                    resolvedCurrency,
                    payment.getStripePaymentIntentId(),
                    stripeEventId,
                    payment.getPaymentStatus().name(),
                    failureReason,
                    Instant.now()
            );
            paymentLifecycleProducer.publishPaymentEvent(paymentEvent);
        } catch (Exception ex) {
            log.error("Failed to publish payment lifecycle event for paymentId {}: {}",
                    payment.getId(), ex.getMessage(), ex);
        }
    }
}


