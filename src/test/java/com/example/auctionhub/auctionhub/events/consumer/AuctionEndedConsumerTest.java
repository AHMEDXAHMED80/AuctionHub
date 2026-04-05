package com.example.auctionhub.auctionhub.events.consumer;

import com.example.auctionhub.auctionhub.events.dto.AuctionEndedEvent;
import com.example.auctionhub.auctionhub.messaging.publisher.NotificationPublisher;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.models.Item.ItemStatus;
import com.example.auctionhub.auctionhub.models.Notifications.NotificationType;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.repository.BidRepository;
import com.example.auctionhub.auctionhub.repository.ItemRepository;
import com.example.auctionhub.auctionhub.service.AuctionWinnerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionEndedConsumerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ItemRepository itemRepository;
    @Mock private AuctionWinnerService auctionWinnerService;
    @Mock private BidRepository bidRepository;
    @Mock private NotificationPublisher notificationPublisher;

    @InjectMocks private AuctionEndedConsumer consumer;

    private Item item;
    private User seller;
    private User bidder1;
    private User bidder2;
    private AuctionEndedEvent eventWithWinner;
    private AuctionEndedEvent eventWithoutWinner;

    @BeforeEach
    void setUp() {
        seller = new User();
        seller.setId(10L);
        seller.setUsername("seller");

        bidder1 = new User();
        bidder1.setId(20L);
        bidder1.setUsername("bidder1");

        bidder2 = new User();
        bidder2.setId(21L);
        bidder2.setUsername("bidder2");

        item = new Item();
        item.setId(100L);
        item.setTitle("Vintage Watch");
        item.setStatus(ItemStatus.ACTIVE);
        item.setSeller(seller);
        item.setEndDate(LocalDateTime.now().minusMinutes(5));

        eventWithWinner = new AuctionEndedEvent(
            100L, "Vintage Watch", 10L,
            new BigDecimal("350.00"), 20L, "bidder1",
            ItemStatus.SOLD, LocalDateTime.now(), true
        );

        eventWithoutWinner = new AuctionEndedEvent(
            100L, "Vintage Watch", 10L,
            BigDecimal.ZERO, null, null,
            ItemStatus.EXPIRED, LocalDateTime.now(), false
        );
    }

    // -----------------------------------------------------------------------
    // Item not found
    // -----------------------------------------------------------------------

    @Test
    void consume_itemNotFound_stopsProcessingImmediately() {
        when(itemRepository.findById(100L)).thenReturn(Optional.empty());

        consumer.consumeAuctionEndedEvent(eventWithWinner);

        verify(auctionWinnerService, never()).processEndedAuctions(any());
        verify(notificationPublisher, never()).publishNotification(any(), any(), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), Optional.ofNullable(any()));
    }

    // -----------------------------------------------------------------------
    // Happy path — auction with a winner
    // -----------------------------------------------------------------------

    @Test
    void consume_withWinner_callsProcessEndedAuctions() {
        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(bidRepository.findAllUserByItemid(100L)).thenReturn(List.of(bidder1, bidder2));

        consumer.consumeAuctionEndedEvent(eventWithWinner);

        verify(auctionWinnerService).processEndedAuctions(item);
    }

    @Test
    void consume_withWinner_broadcastsToWebSocketTopic() {
        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(bidRepository.findAllUserByItemid(100L)).thenReturn(List.of(bidder1));

        consumer.consumeAuctionEndedEvent(eventWithWinner);

        verify(messagingTemplate).convertAndSend(eq("/topic/item/100"), eq(eventWithWinner));
    }

    @Test
    void consume_withWinner_notifiesAllBidders() {
        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(bidRepository.findAllUserByItemid(100L)).thenReturn(List.of(bidder1, bidder2));

        consumer.consumeAuctionEndedEvent(eventWithWinner);

        verify(notificationPublisher).publishNotification(
            eq(20L), contains("Vintage Watch"), eq(NotificationType.ITEM_ENDED));
        verify(notificationPublisher).publishNotification(
            eq(21L), contains("Vintage Watch"), eq(NotificationType.ITEM_ENDED));
    }

    @Test
    void consume_withWinner_notifiesWinnerWithCongrats() {
        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(bidRepository.findAllUserByItemid(100L)).thenReturn(List.of(bidder1));

        consumer.consumeAuctionEndedEvent(eventWithWinner);

        verify(notificationPublisher).publishNotification(
            eq(20L), contains("congrats"), eq(NotificationType.ITEM_WON));
    }

    @Test
    void consume_withWinner_notifiesSeller() {
        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(bidRepository.findAllUserByItemid(100L)).thenReturn(List.of(bidder1));

        consumer.consumeAuctionEndedEvent(eventWithWinner);

        verify(notificationPublisher).publishNotification(
            eq(10L), contains("Vintage Watch"), eq(NotificationType.ITEM_ENDED));
    }

    // -----------------------------------------------------------------------
    // No bids — auction expires
    // -----------------------------------------------------------------------

    @Test
    void consume_noBids_callsProcessEndedAuctions() {
        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(bidRepository.findAllUserByItemid(100L)).thenReturn(List.of());

        consumer.consumeAuctionEndedEvent(eventWithoutWinner);

        verify(auctionWinnerService).processEndedAuctions(item);
    }

    @Test
    void consume_noBids_skipsWinnerNotification() {
        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(bidRepository.findAllUserByItemid(100L)).thenReturn(List.of());

        consumer.consumeAuctionEndedEvent(eventWithoutWinner);

        verify(notificationPublisher, never()).publishNotification(
            any(), contains("congrats"), eq(NotificationType.ITEM_WON));
    }

    @Test
    void consume_noBids_stillNotifiesSeller() {
        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(bidRepository.findAllUserByItemid(100L)).thenReturn(List.of());

        consumer.consumeAuctionEndedEvent(eventWithoutWinner);

        verify(notificationPublisher).publishNotification(
            eq(10L), contains("Vintage Watch"), eq(NotificationType.ITEM_ENDED));
    }

    // -----------------------------------------------------------------------
    // Fault isolation — non-critical steps must not block each other
    // -----------------------------------------------------------------------

    @Test
    void consume_winnerServiceThrows_stillBroadcastsAndNotifies() {
        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(bidRepository.findAllUserByItemid(100L)).thenReturn(List.of(bidder1));
        doThrow(new RuntimeException("DB error")).when(auctionWinnerService).processEndedAuctions(any());

        consumer.consumeAuctionEndedEvent(eventWithWinner);

        // WebSocket and notifications still happen despite winner service failure
        verify(messagingTemplate).convertAndSend(eq("/topic/item/100"), eq(eventWithWinner));
        verify(notificationPublisher, atLeastOnce()).publishNotification(any(), any(), any());
    }

    @Test
    void consume_webSocketThrows_stillSendsNotifications() {
        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(bidRepository.findAllUserByItemid(100L)).thenReturn(List.of(bidder1));
        doThrow(new RuntimeException("WebSocket error"))
            .when(messagingTemplate).convertAndSend(anyString(), Optional.ofNullable(any()));

        consumer.consumeAuctionEndedEvent(eventWithWinner);

        // Notifications still fire even if WebSocket broadcast fails
        verify(notificationPublisher, atLeastOnce()).publishNotification(any(), any(), any());
    }

    @Test
    void consume_multipleBidders_allReceiveEndedNotification() {
        User bidder3 = new User();
        bidder3.setId(22L);

        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(bidRepository.findAllUserByItemid(100L)).thenReturn(List.of(bidder1, bidder2, bidder3));

        consumer.consumeAuctionEndedEvent(eventWithWinner);

        verify(notificationPublisher).publishNotification(eq(20L), any(), eq(NotificationType.ITEM_ENDED));
        verify(notificationPublisher).publishNotification(eq(21L), any(), eq(NotificationType.ITEM_ENDED));
        verify(notificationPublisher).publishNotification(eq(22L), any(), eq(NotificationType.ITEM_ENDED));
    }
}
