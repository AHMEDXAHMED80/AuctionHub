package com.example.auctionhub.auctionhub.events.producer;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.auctionhub.auctionhub.events.dto.PaymentLifecycleEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PaymentLifecycleProducer {
    
    private final KafkaTemplate<String, PaymentLifecycleEvent> kafkaTemplate;

    public PaymentLifecycleProducer(KafkaTemplate<String, PaymentLifecycleEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    private static final String PAYMENT_TOPIC = "payment.events";

    public void publishPaymentEvent(PaymentLifecycleEvent paymentEvent) {
        kafkaTemplate.send(PAYMENT_TOPIC, String.valueOf(paymentEvent.paymentId()), paymentEvent);
        log.info("Published payment event: {}", paymentEvent);
    }

}

