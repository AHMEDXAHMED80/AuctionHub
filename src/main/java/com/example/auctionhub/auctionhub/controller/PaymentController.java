package com.example.auctionhub.auctionhub.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auctionhub.auctionhub.constants.ApiConstants;
import com.example.auctionhub.auctionhub.dto.PaymentDetailsResponse;
import com.example.auctionhub.auctionhub.dto.PaymentRequest;
import com.example.auctionhub.auctionhub.dto.PaymentResponse;
import com.example.auctionhub.auctionhub.dto.RefundRequest;
import com.example.auctionhub.auctionhub.mapper.PaymentMapper;
import com.example.auctionhub.auctionhub.models.Payment;
import com.example.auctionhub.auctionhub.service.PaymentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for handling payment operations.
 * Manages payment creation, refunds, cancellations, and payment history.
 */
@Slf4j
@RestController
@RequestMapping(ApiConstants.PAYMENT_BASE)
public class PaymentController {
    
    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;
    
    /**
     * Constructs a PaymentController with required dependencies.
     *
     * @param paymentService Service for payment logic
     * @param paymentMapper  Mapper for Payment to DTOs
     */
    public PaymentController(PaymentService paymentService, PaymentMapper paymentMapper) {
        this.paymentService = paymentService;
        this.paymentMapper = paymentMapper;
    }
    
    /**
     * Create a new payment for wallet top-up.
     *
     * @param request Payment request with amount
     * @return Payment response with clientSecret for Stripe.js
     */
    @PostMapping(ApiConstants.PAYMENT_CREATE)
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest request) {
        log.info("Payment creation request received - Amount: ${}", request.getAmount());
        try {
            Payment payment = paymentService.createPayment(request.getAmount());
            PaymentResponse response = paymentMapper.toPaymentResponse(payment);
            log.info("Payment creation successful - ID: {}", payment.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid payment request: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        } catch (Exception e) {
            log.error("Payment creation failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create payment: " + e.getMessage());
        }
    }
    
    /**
     * Get payment history for current user
     * 
     * @return List of all payments ordered by date (newest first)
     */
    @GetMapping(ApiConstants.PAYMENT_HISTORY)
    public ResponseEntity<List<PaymentDetailsResponse>> getPaymentHistory() {
        List<PaymentDetailsResponse> response = paymentService.getPaymentHistory();
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get a specific payment by ID
     * 
     * @param paymentId Payment ID
     * @return Payment details
     */
    @GetMapping(ApiConstants.PAYMENT_GET_BY_ID)
    public ResponseEntity<PaymentDetailsResponse> getPaymentById(@PathVariable Long paymentId) {
        Payment payment = paymentService.getPaymentById(paymentId);
        PaymentDetailsResponse response = paymentMapper.toPaymentDetailsResponse(payment);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Cancel a pending payment
     * @param paymentId Payment ID to cancel
     * @return Canceled payment details
     */
    @PostMapping(ApiConstants.PAYMENT_CANCEL)
    public ResponseEntity<PaymentDetailsResponse> cancelPayment(@PathVariable Long paymentId) {
        try {
            Payment payment = paymentService.cancelPayment(paymentId);
            PaymentDetailsResponse response = paymentMapper.toPaymentDetailsResponse(payment);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to cancel payment: " + e.getMessage());
        }
    }
    
    /**
     * Refund a successful payment
     * 
     * @param paymentId Payment ID to refund
     * @param request Refund request with reason
     * @return Refunded payment details
     */
    @PostMapping(ApiConstants.PAYMENT_REFUND)
    public ResponseEntity<PaymentDetailsResponse> refundPayment(
            @PathVariable Long paymentId,
            @RequestBody RefundRequest request) {
        try {
            Payment payment = paymentService.refundPayment(paymentId, request.getReason());
            PaymentDetailsResponse response = paymentMapper.toPaymentDetailsResponse(payment);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to refund payment: " + e.getMessage());
        }
    }
}
