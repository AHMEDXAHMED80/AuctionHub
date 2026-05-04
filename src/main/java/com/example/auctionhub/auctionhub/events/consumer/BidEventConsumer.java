package com.example.auctionhub.auctionhub.events.consumer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.example.auctionhub.auctionhub.events.dto.BidEvent;
import com.example.auctionhub.auctionhub.repository.BidRepository;
import com.example.auctionhub.auctionhub.service.NotificationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BidEventConsumer {

    private final SimpMessagingTemplate messagingTemplate;
    private final BidRepository bidRepository;
    private final NotificationService notificationService;


    public BidEventConsumer(
            SimpMessagingTemplate messagingTemplate,
            BidRepository bidRepository,
            NotificationService notificationService) {
        this.messagingTemplate = messagingTemplate;
        this.bidRepository = bidRepository;
        this.notificationService = notificationService;
    }
    
    @KafkaListener(topics = "bid.events", groupId = "bid-notification-group")
    public void consumeBidEvent(BidEvent bidEvent) {
        log.info("Consumed bid event: {}", bidEvent);
        broadcastToViewers(bidEvent);
        notifyPreviousHigestBidder(bidEvent);
    }

    private void broadcastToViewers(BidEvent bidEvent) {
        messagingTemplate.convertAndSend("/topic/item/" + bidEvent.getItemId(), bidEvent);
        log.info("Broadcasted bid to /topic/item/{}", bidEvent.getItemId());
    }

    private void notifyPreviousHigestBidder(BidEvent bidEvent) {
        
        if (bidEvent.getPrevHighstBidUserId() == null ) {
            return;
        }

        notificationService.notifyOutbid(bidEvent.getPrevHighstBidUserId(), bidEvent.getItemName());

    }
    
    
}