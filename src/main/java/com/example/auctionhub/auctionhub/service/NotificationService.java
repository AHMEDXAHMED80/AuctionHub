package com.example.auctionhub.auctionhub.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.auctionhub.auctionhub.dto.NotificationResponseDto;
import com.example.auctionhub.auctionhub.mapper.NotificationMapper;
import com.example.auctionhub.auctionhub.messaging.publisher.NotificationPublisher;
import com.example.auctionhub.auctionhub.models.Notifications;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.models.Notifications.NotificationType;
import com.example.auctionhub.auctionhub.repository.NotificationRepository;
import com.example.auctionhub.auctionhub.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Application service for notification workflows.
 *
 * It builds domain-specific notification messages, validates recipient users,
 * and publishes notification events to RabbitMQ for asynchronous processing.
 * Read operations (list and mark-as-read) are served from the notifications repository.
 */
@Service
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationPublisher notificationPublisher;
    private final NotificationMapper notificationMapper;

    private static final String NOTIFY_OUTBID_TEMPLATE = "You've been outbid on %s. A higher bid has been placed.";
    private static final String NOTIFY_BID_PLACED_TEMPLATE = "%s placed a new bid of %s on %s.";
    private static final String NOTIFY_AUCTION_WON_TEMPLATE = "Congratulations! You won the auction for %s with a bid of %s.";
    private static final String NOTIFY_AUCTION_ENDED_FOR_BIDDER_TEMPLATE = "Auction ended for %s. Check the final result in your bid history.";
    private static final String NOTIFY_AUCTION_ENDED_FOR_SELLER_TEMPLATE = "Your auction for %s has ended. Final highest bid: %s.";
    private static final String NOTIFY_PAYMENT_RECEIVED_TEMPLATE = "Payment received: %s has been added to your wallet.";

    /**
     * Creates a NotificationService with required dependencies.
     */
    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            NotificationPublisher notificationPublisher,
            NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationPublisher = notificationPublisher;
        this.notificationMapper = notificationMapper;

    }

    private void notifyUser(Long userid, String message, Notifications.NotificationType type){
        try {
            if (!userRepository.existsById(userid)) {
                log.warn("Cannot notify missing userId={} type={}", userid, type);
                throw new IllegalArgumentException("User not found with id: " + userid);
            }
            publishAsync(userid, message, type);
            log.info("Published notification type={} to userId={}", type, userid);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed notifyUser userId={} type={}: {}", userid, type, e.getMessage(), e);
            throw new RuntimeException("Failed to publish notification for user " + userid, e);
        }
    }

    private void notifyUsers(List<Long> userIds, Notifications.NotificationType type, String message, Long excludedUser){
        try {
            if (userIds == null || userIds.isEmpty()) {
                log.debug("notifyUsers called with empty userIds for type={}", type);
                return;
            }

            Set<Long> uniqueUserIds = new HashSet<>(userIds);
            if (excludedUser != null) {
                uniqueUserIds.remove(excludedUser);
            }
            List<User> users = userRepository.findAllById(uniqueUserIds);
            Set<Long> foundUserIds = users.stream().map(User::getId).collect(Collectors.toSet());
            if (foundUserIds.size() != uniqueUserIds.size()) {
                Set<Long> missingUserIds = new HashSet<>(uniqueUserIds);
                missingUserIds.removeAll(foundUserIds);
                log.warn("Cannot notify missing userIds={} type={}", missingUserIds, type);
                throw new IllegalArgumentException("Users not found with ids: " + missingUserIds);
            }

            for (User user : users) {
                publishAsync(user.getId(), message, type);
            }
            log.info("Published notification type={} to {} users", type, users.size());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed notifyUsers type={} size={}: {}", type, userIds != null ? userIds.size() : 0,
                    e.getMessage(), e);
            throw new RuntimeException("Failed to publish notifications to users", e);
        }

    }

    /**
     * Sends an outbid notification to a single user.
     */
    public void notifyOutbid(Long userId, String itemName){
        String message = String.format(NOTIFY_OUTBID_TEMPLATE, itemName);
        notifyUser(userId, message, NotificationType.BID_OUTBID);
    }

    /**
     * Sends a "new bid placed" notification to a single user.
     */
    public void notifyBidPlaced(Long userId, String bidderName, BigDecimal bidAmount, String itemName){
        String message = String.format(NOTIFY_BID_PLACED_TEMPLATE, bidderName, bidAmount, itemName);
        notifyUser(userId, message, NotificationType.BID_PLACED);
    }

    /**
     * Sends an auction won notification to the winner.
     */
    public void notifyAuctionWon(Long winnerUserId, String itemName, BigDecimal winningBid){
        String message = String.format(NOTIFY_AUCTION_WON_TEMPLATE, itemName, winningBid);
        notifyUser(winnerUserId, message, NotificationType.ITEM_WON);
    }

    /**
     * Sends auction-ended notification to bidder users.
     */
    public void notifyAuctionEndedForBidder(List<Long> bidderUserIds, String itemName, Long excludedUser){
        String message = String.format(NOTIFY_AUCTION_ENDED_FOR_BIDDER_TEMPLATE, itemName);
        notifyUsers(bidderUserIds, NotificationType.ITEM_LOST, message, excludedUser);
    }

    /**
     * Sends auction-ended summary notification to seller.
     */
    public void notifyAuctionEndedForSeller(Long sellerUserId, String itemName, BigDecimal finalBid){
        String message = String.format(NOTIFY_AUCTION_ENDED_FOR_SELLER_TEMPLATE, itemName, finalBid);
        notifyUser(sellerUserId, message, NotificationType.ITEM_ENDED);
    }

    /**
     * Sends wallet payment-received notification to user.
     */
    public void notifyPaymentReceived(Long userId, String paymentRef){
        String message = String.format(NOTIFY_PAYMENT_RECEIVED_TEMPLATE, paymentRef);
        notifyUser(userId, message, NotificationType.PAYMENT_RECEIVED);
    }

    /**
     * Returns notifications for a specific user ordered by newest first.
     */
    public Page<NotificationResponseDto> getNotifications(Long userId, Pageable pageable) {
        try {
            Page<Notifications> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
            return notifications.map(notificationMapper::toNotificationResponseDto);
        } catch (Exception e) {
            log.error("Failed getNotifications userId={}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch notifications for user " + userId, e);
        }
    }

    /**
     * Marks all unread notifications as read for a specific user.
     */
    public void markAllAsRead(Long userId){
        try {
            int updatedRows = notificationRepository.markAllAsRead(userId);
            log.info("Marked {} notifications as read for userId={}", updatedRows, userId);
        } catch (Exception e) {
            log.error("Failed markAllAsRead userId={}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to mark notifications as read for user " + userId, e);
        }
    }

    private void publishAsync(Long userId, String message, Notifications.NotificationType type){
        notificationPublisher.publishNotification(userId, message, type);
    }

}
