package com.example.auctionhub.auctionhub.events.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.auctionhub.auctionhub.events.dto.PaymentLifecycleEvent;
import com.example.auctionhub.auctionhub.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentLifecycleConsumer {

    private static final String PAYMENT_TOPIC = "payment.events";
    private static final String PAYMENT_NOTIFICATION_GROUP = "payment-notification-group";

    private final NotificationService notificationService;

    @KafkaListener(topics = PAYMENT_TOPIC, groupId = PAYMENT_NOTIFICATION_GROUP)
    public void consumePaymentEvent(PaymentLifecycleEvent event) {
        if (event == null || event.eventType() == null || event.userId() == null) {
            log.warn("Skipping invalid payment lifecycle event: {}", event);
            return;
        }

        if (event.eventType() != PaymentLifecycleEvent.EventType.PAYMENT_SUCCEEDED) {
            return;
        }

        String paymentRef = (event.stripePaymentIntentId() != null && !event.stripePaymentIntentId().isBlank())
                ? event.stripePaymentIntentId()
                : String.valueOf(event.paymentId());

        try {
            notificationService.notifyPaymentReceived(event.userId(), paymentRef);
            log.info("Queued payment success notification for user {} paymentRef {}", event.userId(), paymentRef);
        } catch (Exception e) {
            log.error("Failed to process payment success notification for event {}: {}", event, e.getMessage(), e);
        }
    }
}
