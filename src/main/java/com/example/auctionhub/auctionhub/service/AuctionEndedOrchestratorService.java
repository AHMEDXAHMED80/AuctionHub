package com.example.auctionhub.auctionhub.service;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.example.auctionhub.auctionhub.events.dto.AuctionEndedEvent;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.repository.BidRepository;
import com.example.auctionhub.auctionhub.repository.ItemRepository;

@Service
public class AuctionEndedOrchestratorService {
    private static final Logger logger = LoggerFactory.getLogger(AuctionEndedOrchestratorService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ItemRepository itemRepository;
    private final AuctionWinnerService auctionWinnerService;
    private final BidRepository bidRepository;
    private final NotificationService notificationService;
    public AuctionEndedOrchestratorService(
            SimpMessagingTemplate messagingTemplate,
            ItemRepository itemRepository,
            AuctionWinnerService auctionWinnerService,
            BidRepository bidRepository,
            NotificationService notificationService, WalletService walletService) {
        this.messagingTemplate = messagingTemplate;
        this.itemRepository = itemRepository;
        this.auctionWinnerService = auctionWinnerService;
        this.bidRepository = bidRepository;
        this.notificationService = notificationService;
    }

    public void handleAuctionEnded(AuctionEndedEvent event) {
        try {
            if (event == null || event.getItemId() == null) {
                logger.warn("Skipping auction-ended handling due to invalid event: {}", event);
                return;
            }

            logger.info("Handling auction-ended event for item {}", event.getItemId());

            Item item = itemRepository.findById(event.getItemId()).orElse(null);
            if (item == null) {
                logger.warn("Item not found for auction-ended event. itemId={}", event.getItemId());
                return;
            }

            processWinner(item, event);
            broadcastToViewers(event);
            notifyBidders(event);
            notifyWinner(event);
            notifySeller(event);

            logger.info("Completed auction-ended orchestration for item {}", event.getItemId());
        } catch (Exception e) {
            logger.error("Failed handling auction-ended event for item {}: {}",
                    event != null ? event.getItemId() : null, e.getMessage(), e);
            throw new RuntimeException("Failed to handle auction-ended event", e);
        }
    }


    private void processWinner(Item item, AuctionEndedEvent event) {
        try {
            logger.info("Processing winner settlement for item {}", item.getId());

            if (event.getWinningBidderId() == null) {
                logger.info("No winning bidder in event for item {}", item.getId());
            } else {
                logger.info(
                    "Winner candidate for item {} -> userId={}, finalBid={}",
                    item.getId(),
                    event.getWinningBidderId(),
                    event.getFinalBidAmount()
                );
            }

            // Single source of truth for winner/bid/item/fund transfer updates
            auctionWinnerService.processEndedAuctions(item);

            logger.info("Winner settlement completed for item {}", item.getId());
        } catch (Exception e) {
            logger.error("Failed to process winner for item {}: {}", item.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to process winner for item " + item.getId(), e);
        }
    }

    private void notifyBidders(AuctionEndedEvent event) {
        try {
            logger.info("Preparing bidder notifications for item {}", event.getItemId());

            List<User> bidders = bidRepository.findAllUserByItemid(event.getItemId());
            if (bidders == null || bidders.isEmpty()) {
                logger.info("No bidders found for item {}", event.getItemId());
                return;
            }

            List<Long> bidderIds = bidders.stream()
                    .map(User::getId)
                    .distinct()
                    .toList();

            notificationService.notifyAuctionEndedForBidder(
                    bidderIds,
                    event.getItemName(),
                    event.getWinningBidderId()
            );

            logger.info("Queued bidder notifications for item {} to {} users",
                    event.getItemId(), bidderIds.size());
        } catch (Exception e) {
            logger.error("Failed to notify bidders for item {}: {}", event.getItemId(), e.getMessage(), e);
            throw new RuntimeException("Failed to notify bidders for item " + event.getItemId(), e);
        }
    }


    private void notifyWinner(AuctionEndedEvent event) {
        try {
            if (event.getWinningBidderId() == null) {
                logger.info("No winner to notify for item {}", event.getItemId());
                return;
            }

            if (event.getFinalBidAmount() == null) {
                logger.warn("Winner exists but finalBidAmount is null for item {}", event.getItemId());
                return;
            }

            notificationService.notifyAuctionWon(
                    event.getWinningBidderId(),
                    event.getItemName(),
                    event.getFinalBidAmount()
            );

            logger.info("Queued winner notification for item {} to user {}",
                    event.getItemId(), event.getWinningBidderId());
        } catch (Exception e) {
            logger.error("Failed to notify winner for item {}: {}", event.getItemId(), e.getMessage(), e);
            throw new RuntimeException("Failed to notify winner for item " + event.getItemId(), e);
        }
    }

    private void notifySeller(AuctionEndedEvent event) {
        try {
            if (event.getSellerId() == null) {
                logger.warn("No sellerId in auction-ended event for item {}", event.getItemId());
                return;
            }

            if (event.getFinalBidAmount() == null) {
                logger.info("No final bid for item {}. Skipping seller final-bid notification.", event.getItemId());
                return;
            }

            notificationService.notifyAuctionEndedForSeller(
                    event.getSellerId(),
                    event.getItemName(),
                    event.getFinalBidAmount()
            );

            logger.info("Queued seller notification for item {} to seller {}",
                    event.getItemId(), event.getSellerId());
        } catch (Exception e) {
            logger.error("Failed to notify seller for item {}: {}", event.getItemId(), e.getMessage(), e);
            throw new RuntimeException("Failed to notify seller for item " + event.getItemId(), e);
        }
    }


    private void broadcastToViewers(AuctionEndedEvent event) {
        try {
            if (event == null || event.getItemId() == null) {
                logger.warn("Skipping broadcast: invalid auction-ended event {}", event);
                return;
            }

            messagingTemplate.convertAndSend("/topic/item/" + event.getItemId(), event);
            logger.info("Broadcasted auction-ended event to /topic/item/{}", event.getItemId());
        } catch (Exception e) {
            logger.error("Failed to broadcast auction-ended event for item {}: {}",
                    event != null ? event.getItemId() : null, e.getMessage(), e);
            throw new RuntimeException("Failed to broadcast auction-ended event", e);
        }
    }



}
