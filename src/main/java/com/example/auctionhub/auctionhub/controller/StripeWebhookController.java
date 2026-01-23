package com.example.auctionhub.auctionhub.controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.auctionhub.auctionhub.service.StripeService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;

/**
 * Controller for handling Stripe webhook events
 * Receives payment notifications from Stripe and processes them
 */
@RestController
@RequestMapping("/api/stripe")
public class StripeWebhookController {
    
    @Value("${STRIPE.WEBHOOK.SECRET}")
    private String webhookSecret;
    
    private final StripeService stripeService;
    
    public StripeWebhookController(StripeService stripeService) {
        this.stripeService = stripeService;
    }
    
    /**
     * Webhook endpoint for receiving Stripe events
     * 
     * @param payload Raw request body from Stripe
     * @param signatureHeader Stripe-Signature header for verification
     * @return 200 OK if event processed successfully, 400 if signature invalid
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signatureHeader) {
        
        Event event;
        
        try {
            // Verify webhook signature to ensure it's from Stripe
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            // Invalid signature - this is not from Stripe
            System.err.println("⚠️ Webhook signature verification failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid signature");
        } catch (Exception e) {
            System.err.println("⚠️ Error parsing webhook payload: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid payload");
        }
        
        // Log received event
        System.out.println("📨 Received webhook event: " + event.getType() + 
                          " (ID: " + event.getId() + ")");
        
        // Process the event
        try {
            stripeService.handleStripeEvent(event);
            return ResponseEntity.ok("Event processed successfully");
        } catch (Exception e) {
            // Log error but still return 200 to prevent Stripe from retrying
            System.err.println("❌ Error processing webhook event: " + e.getMessage());
            e.printStackTrace();
            
            // Return 200 OK to acknowledge receipt even if processing failed
            // This prevents Stripe from retrying the same event
            return ResponseEntity.ok("Event received but processing failed");
        }
    }
}
