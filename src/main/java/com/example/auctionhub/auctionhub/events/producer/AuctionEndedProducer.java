package com.example.auctionhub.auctionhub.events.producer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import com.example.auctionhub.auctionhub.events.dto.AuctionEndedEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AuctionEndedProducer {
    private final KafkaTemplate<String, AuctionEndedEvent> auctionKafkaTemplate;

    public AuctionEndedProducer(KafkaTemplate<String, AuctionEndedEvent> auctionKafkaTemplate) {
        this.auctionKafkaTemplate = auctionKafkaTemplate;
    }
    private static final String AUCTION_ENDED_TOPIC = "auction-ended";

    public void sendAuctionEndedEvent(AuctionEndedEvent event) {
        log.info("Sending auction ended event: {}", event);
        auctionKafkaTemplate.send(AUCTION_ENDED_TOPIC, event.getItemId().toString(), event);
        log.info("Auction ended event sent successfully");
    }
}
