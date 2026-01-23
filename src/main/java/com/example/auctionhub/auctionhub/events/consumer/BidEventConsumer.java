package com.example.auctionhub.auctionhub.events.consumer;
import java.util.List;
import com.example.auctionhub.auctionhub.models.Notifications;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.example.auctionhub.auctionhub.events.dto.BidEvent;
import com.example.auctionhub.auctionhub.messaging.publisher.NotificationPublisher;
import com.example.auctionhub.auctionhub.models.Bid;
import com.example.auctionhub.auctionhub.repository.BidRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidEventConsumer {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final BidRepository bidRepository;
    private final NotificationPublisher notificationPublisher;
    
    @KafkaListener(topics = "bid.events", groupId = "bid-notification-group")
    public void consumeBidEvent(BidEvent bidEvent) {
        log.info("Consumed bid event: {}", bidEvent);
        broadcastToViewers(bidEvent);
        notifyPreviousBidders(bidEvent);
    }

    private void broadcastToViewers(BidEvent bidEvent) {
        messagingTemplate.convertAndSend("/topic/item/" + bidEvent.getItemId(), bidEvent);
        log.info("Broadcasted bid to /topic/item/{}", bidEvent.getItemId());
    }

    private void notifyPreviousBidders(BidEvent bidEvent) {
        Long currentUserId = bidEvent.getUserId();
        Bid prevHighestBidder = bidRepository.findHighestBidByItemId(bidEvent.getItemId()).orElse(null);
        
        // Check null first to avoid NullPointerException
        if (prevHighestBidder != null) {
            Long prevHighestBidderId = prevHighestBidder.getUser().getId();
            
            // Get all other bidders (excluding current and previous highest)
            List<Long> allPreviousBidders = bidRepository.findDistinctUserIdsByItemId(
                bidEvent.getItemId(), 
                currentUserId, 
                prevHighestBidderId
            );

            // Notify the previous highest bidder (they were outbid)
            notificationPublisher.publishNotification(
                prevHighestBidderId,
                "You've been outbid on " + bidEvent.getItemName() + "!",
                Notifications.NotificationType.BID_OUTBID
            );

            // Notify all other bidders
            if (allPreviousBidders != null && !allPreviousBidders.isEmpty()) {
                for (Long userId : allPreviousBidders) {
                    notificationPublisher.publishNotification(
                        userId,
                        "User: " + bidEvent.getUsername() + " placed a new bid on " + bidEvent.getItemName(),
                        Notifications.NotificationType.BID_PLACED
                    );
                }
            }
            
            log.info("Notified {} previous bidders for item {}", 
                (allPreviousBidders != null ? allPreviousBidders.size() : 0) + 1, 
                bidEvent.getItemId());
        } else {
            log.info("No previous bidders to notify for item {}", bidEvent.getItemId());
        }
    }
    
    
}