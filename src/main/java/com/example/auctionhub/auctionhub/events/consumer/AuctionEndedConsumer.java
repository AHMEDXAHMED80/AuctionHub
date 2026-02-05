package com.example.auctionhub.auctionhub.events.consumer;
import java.util.List;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import com.example.auctionhub.auctionhub.events.dto.AuctionEndedEvent;
import com.example.auctionhub.auctionhub.messaging.publisher.NotificationPublisher;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.models.Notifications.NotificationType;
import com.example.auctionhub.auctionhub.repository.ItemRepository;
import com.example.auctionhub.auctionhub.service.AuctionWinnerService;
import com.example.auctionhub.auctionhub.repository.BidRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AuctionEndedConsumer {
    private final SimpMessagingTemplate messagingTemplate;
    private final ItemRepository itemRepository;
    private final AuctionWinnerService auctionWinnerService;
    private final BidRepository bidRepository;
    private final NotificationPublisher notificationPublisher;

    public AuctionEndedConsumer(SimpMessagingTemplate messagingTemplate,
                                ItemRepository itemRepository,
                                AuctionWinnerService auctionWinnerService,
                                BidRepository bidRepository,
                                NotificationPublisher notificationPublisher) {
        this.messagingTemplate = messagingTemplate;
        this.itemRepository = itemRepository;
        this.auctionWinnerService = auctionWinnerService;
        this.bidRepository = bidRepository;
        this.notificationPublisher = notificationPublisher;
    }

    @KafkaListener(topics = "auction-ended", groupId = "auction-ended-group")
    public void consumeAuctionEndedEvent(AuctionEndedEvent event){
        log.info("Consumed auction ended event: {}", event);
        Item item = itemRepository.findById(event.getItemId()).orElse(null);
        if (item == null) {
            log.warn("Item not found with ID: {}", event.getItemId());
            return;
        }

        // Process auction winner (critical operation)
        try {
            auctionWinnerService.processEndedAuctions(item);
        } catch (Exception e) {
            log.error("Failed to process auction winner for item {}: {}", event.getItemId(), e.getMessage(), e);
            // Don't return - still try to send notifications
        }

        // Broadcast and notifications (non-critical, shouldn't stop other notifications)
        try {
            broadcastToViewers(event);
        } catch (Exception e) {
            log.error("Failed to broadcast to viewers for item {}: {}", event.getItemId(), e.getMessage());
        }

        try {
            NotifyAllBidders(event);
        } catch (Exception e) {
            log.error("Failed to notify bidders for item {}: {}", event.getItemId(), e.getMessage());
        }

        try {
            NotifythewinnerBidders(event);
        } catch (Exception e) {
            log.error("Failed to notify winner for item {}: {}", event.getItemId(), e.getMessage());
        }

        try {
            NotifySeller(event);
        } catch (Exception e) {
            log.error("Failed to notify seller for item {}: {}", event.getItemId(), e.getMessage());
        }

        log.info("Finished processing auction ended event for item {}", event.getItemId());
    }

    private void broadcastToViewers(AuctionEndedEvent event){
        messagingTemplate.convertAndSend("/topic/item/" + event.getItemId(), event);
        log.info("Broadcasted auction ended event to /topic/item/{}", event.getItemId());
    }

    private void NotifyAllBidders(AuctionEndedEvent event){
        log.info("Notifying all bidders for end of item {}", event.getItemId());
    
        List<User> bidders = bidRepository.findAllUserByItemid(event.getItemId());
        log.info("Found {} bidders for item {}", bidders.size(), event.getItemId());
        if (bidders == null || bidders.isEmpty()) {
            log.info("No bidders found for item {}", event.getItemId());
            return;
        }

        for (User bidder : bidders) {
            notificationPublisher.publishNotification(
            bidder.getId(),
            "Auction: " + event.getItemName() + " has ended",
            NotificationType.ITEM_ENDED
            );  
        }

    }

    private void NotifythewinnerBidders(AuctionEndedEvent event){
        log.info("Notifying winner bidders for end of item {}", event.getItemId());

        if (event.getWinningBidderId()==null) {
            log.info("No winner bidders found for item {}", event.getItemId());
            return;
        }
            notificationPublisher.publishNotification(
            event.getWinningBidderId(),
            "congrats you won the auction: " + event.getItemName(),
            NotificationType.ITEM_WON

        );
    }

    private void NotifySeller(AuctionEndedEvent event){
        log.info("Notifying seller for end of item {}", event.getItemId());

        notificationPublisher.publishNotification(
            event.getSellerId(),
            "Auction: " + event.getItemName() + " has ended",
            NotificationType.ITEM_ENDED
        );
        
    }




}
