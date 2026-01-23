package com.example.auctionhub.auctionhub.events.producer;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.auctionhub.auctionhub.events.dto.BidEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidEventProducer {
    private final KafkaTemplate<String, BidEvent> kafkaTemplate;
    private static final String BID_TOPIC = "bid.events";

    public void publishBidEvent(BidEvent bidEvent) {
        log.info("Publishing bid event to Kafka: {}", bidEvent);
        kafkaTemplate.send(BID_TOPIC, bidEvent.getItemId().toString(), bidEvent);
        log.info("Bid event published successfully");
    }
}