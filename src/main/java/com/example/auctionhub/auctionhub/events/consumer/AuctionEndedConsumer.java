package com.example.auctionhub.auctionhub.events.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.auctionhub.auctionhub.events.dto.AuctionEndedEvent;
import com.example.auctionhub.auctionhub.service.AuctionEndedOrchestratorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEndedConsumer {

    private static final String AUCTION_ENDED_TOPIC = "auction-ended";
    private static final String AUCTION_ENDED_GROUP = "auction-ended-group";

    private final AuctionEndedOrchestratorService auctionEndedOrchestratorService;

    @KafkaListener(topics = AUCTION_ENDED_TOPIC, groupId = AUCTION_ENDED_GROUP)
    public void consumeAuctionEndedEvent(AuctionEndedEvent event) {
        if (event == null || event.getItemId() == null) {
            log.warn("Skipping auction-ended event due to missing payload or itemId: {}", event);
            return;
        }

        Long itemId = event.getItemId();
        log.info("Consumed auction-ended event for item {}", itemId);
        try {
            auctionEndedOrchestratorService.handleAuctionEnded(event);
            log.info("Finished processing auction-ended event for item {}", itemId);
        } catch (Exception e) {
            log.error("Failed to process auction-ended event for item {}: {}", itemId, e.getMessage(), e);
        }
    }
}
