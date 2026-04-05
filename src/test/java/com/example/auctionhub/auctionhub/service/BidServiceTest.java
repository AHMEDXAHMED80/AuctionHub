package com.example.auctionhub.auctionhub.service;

import com.example.auctionhub.auctionhub.events.producer.BidEventProducer;
import com.example.auctionhub.auctionhub.mapper.BidMapper;
import com.example.auctionhub.auctionhub.mapper.ItemBidderMapper;
import com.example.auctionhub.auctionhub.mapper.UserBidHistoryMapper;
import com.example.auctionhub.auctionhub.models.Bid;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.models.Item.ItemStatus;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.models.Wallet;
import com.example.auctionhub.auctionhub.repository.BidRepository;
import com.example.auctionhub.auctionhub.repository.ItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock private ItemRepository itemRepository;
    @Mock private BidRepository bidRepository;
    @Mock private BidEventProducer bidEventProducer;
    @Mock private BidMapper bidMapper;
    @Mock private ItemBidderMapper itemBidderMapper;
    @Mock private WalletService walletService;
    @Mock private UserBidHistoryMapper userBidHistoryMapper;
    @Mock private ItemImageService itemImageService;

    @InjectMocks private BidService bidService;

    private User seller;
    private User bidder;
    private Item item;
    private Wallet bidderWallet;

    @BeforeEach
    void setUp() {
        seller = new User();
        seller.setId(1L);
        seller.setUsername("seller");
        seller.setRole(User.roles.POSTER);

        bidderWallet = new Wallet();
        bidderWallet.setAvailableBalance(new BigDecimal("500.00"));
        bidderWallet.setFrozenBalance(BigDecimal.ZERO);

        bidder = new User();
        bidder.setId(2L);
        bidder.setUsername("bidder");
        bidder.setRole(User.roles.USER);
        bidder.setWallet(bidderWallet);

        item = new Item();
        item.setId(10L);
        item.setTitle("Test Item");
        item.setStatus(ItemStatus.ACTIVE);
        item.setCurrentHighestBid(new BigDecimal("100.00"));
        item.setStartingPrice(new BigDecimal("100.00"));
        item.setEndDate(LocalDateTime.now().plusDays(1));
        item.setSeller(seller);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // -----------------------------------------------------------------------
    // placeBid — early guards (before authentication)
    // -----------------------------------------------------------------------

    @Test
    void placeBid_itemNotFound_throwsRuntimeException() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> bidService.placeBid(99L, new BigDecimal("200.00")));
    }

    @Test
    void placeBid_itemNotActive_throwsIllegalStateException() {
        item.setStatus(ItemStatus.EXPIRED);
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        assertThrows(IllegalStateException.class,
            () -> bidService.placeBid(10L, new BigDecimal("200.00")));
    }

    // -----------------------------------------------------------------------
    // placeBid — validation inside the retry loop
    // -----------------------------------------------------------------------

    @Test
    void placeBid_userHasNoWallet_throwsRuntimeException() {
        bidder.setWallet(null);
        authenticateAs(bidder);
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> bidService.placeBid(10L, new BigDecimal("200.00")));
        assertTrue(ex.getMessage().contains("wallet"));
    }

    @Test
    void placeBid_auctionAlreadyEnded_throwsRuntimeException() {
        item.setEndDate(LocalDateTime.now().minusHours(1));
        authenticateAs(bidder);
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> bidService.placeBid(10L, new BigDecimal("200.00")));
        assertTrue(ex.getMessage().contains("Auction has ended"));
    }

    @Test
    void placeBid_sellerBidsOnOwnItem_throwsRuntimeException() {
        seller.setWallet(bidderWallet);
        authenticateAs(seller);
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> bidService.placeBid(10L, new BigDecimal("200.00")));
        assertTrue(ex.getMessage().contains("cannot bid on your own item"));
    }

    @Test
    void placeBid_amountEqualToCurrentHighest_throwsRuntimeException() {
        authenticateAs(bidder);
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> bidService.placeBid(10L, new BigDecimal("100.00")));
        assertTrue(ex.getMessage().contains("higher than"));
    }

    @Test
    void placeBid_amountBelowCurrentHighest_throwsRuntimeException() {
        authenticateAs(bidder);
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> bidService.placeBid(10L, new BigDecimal("50.00")));
        assertTrue(ex.getMessage().contains("higher than"));
    }

    @Test
    void placeBid_insufficientBalance_throwsRuntimeException() {
        bidderWallet.setAvailableBalance(new BigDecimal("50.00"));
        authenticateAs(bidder);
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> bidService.placeBid(10L, new BigDecimal("200.00")));
        assertTrue(ex.getMessage().contains("Insufficient balance"));
    }

    @Test
    void placeBid_posterRoleCannotBid_throwsRuntimeException() {
        User poster = new User();
        poster.setId(3L);
        poster.setUsername("another_poster");
        poster.setRole(User.roles.POSTER);
        poster.setWallet(bidderWallet);
        authenticateAs(poster);
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> bidService.placeBid(10L, new BigDecimal("200.00")));
        assertTrue(ex.getMessage().contains("not allowed to bid"));
    }

    // -----------------------------------------------------------------------
    // placeBid — happy path
    // -----------------------------------------------------------------------

    @Test
    void placeBid_firstBidOnItem_savesBidAndPublishesEvent() {
        authenticateAs(bidder);
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(bidRepository.findHighestBidByItemId(10L)).thenReturn(Optional.empty());
        when(bidRepository.save(any(Bid.class))).thenReturn(new Bid());
        when(itemRepository.save(any(Item.class))).thenReturn(item);
        doNothing().when(bidEventProducer).publishBidEvent(any());
        doNothing().when(walletService).addToFrozenBalance(any(), any());

        assertDoesNotThrow(() -> bidService.placeBid(10L, new BigDecimal("200.00")));

        verify(bidRepository).save(any(Bid.class));
        verify(itemRepository).save(any(Item.class));
        verify(bidEventProducer).publishBidEvent(any());
        verify(walletService).addToFrozenBalance(eq(2L), eq(new BigDecimal("200.00")));
        verify(walletService, never()).deductFromFrozenBalance(any(), any());
    }

    @Test
    void placeBid_outbidsPreviousBidder_unfreezesPreviousBidderFunds() {
        authenticateAs(bidder);

        User previousBidder = new User();
        previousBidder.setId(3L);
        Bid previousBid = new Bid();
        previousBid.setUser(previousBidder);
        previousBid.setBidAmount(new BigDecimal("150.00"));

        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(bidRepository.findHighestBidByItemId(10L)).thenReturn(Optional.of(previousBid));
        when(bidRepository.save(any(Bid.class))).thenReturn(new Bid());
        when(itemRepository.save(any(Item.class))).thenReturn(item);
        doNothing().when(bidEventProducer).publishBidEvent(any());
        doNothing().when(walletService).addToFrozenBalance(any(), any());
        doNothing().when(walletService).deductFromFrozenBalance(any(), any());

        bidService.placeBid(10L, new BigDecimal("200.00"));

        verify(walletService).deductFromFrozenBalance(eq(3L), eq(new BigDecimal("150.00")));
        verify(walletService).addToFrozenBalance(eq(2L), eq(new BigDecimal("200.00")));
    }

    @Test
    void placeBid_updatesItemCurrentHighestBid() {
        authenticateAs(bidder);
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(bidRepository.findHighestBidByItemId(10L)).thenReturn(Optional.empty());
        when(bidRepository.save(any(Bid.class))).thenReturn(new Bid());
        when(itemRepository.save(any(Item.class))).thenReturn(item);
        doNothing().when(bidEventProducer).publishBidEvent(any());
        doNothing().when(walletService).addToFrozenBalance(any(), any());

        bidService.placeBid(10L, new BigDecimal("200.00"));

        assertEquals(new BigDecimal("200.00"), item.getCurrentHighestBid());
    }

    // -----------------------------------------------------------------------
    // getHighestBid
    // -----------------------------------------------------------------------

    @Test
    void getHighestBid_bidExists_returnsAmount() {
        Bid bid = new Bid();
        bid.setBidAmount(new BigDecimal("250.00"));
        when(bidRepository.findHighestBidByItemId(10L)).thenReturn(Optional.of(bid));

        assertEquals(new BigDecimal("250.00"), bidService.getHighestBid(10L));
    }

    @Test
    void getHighestBid_noBids_returnsZero() {
        when(bidRepository.findHighestBidByItemId(10L)).thenReturn(Optional.empty());

        assertEquals(BigDecimal.ZERO, bidService.getHighestBid(10L));
    }

    // -----------------------------------------------------------------------
    // isUserWinning
    // -----------------------------------------------------------------------

    @Test
    void isUserWinning_userIsHighestBidder_returnsTrue() {
        Bid bid = new Bid();
        bid.setUser(bidder);
        when(bidRepository.findHighestBidByItemId(10L)).thenReturn(Optional.of(bid));

        assertTrue(bidService.isUserWinning(10L, 2L));
    }

    @Test
    void isUserWinning_differentUserIsHighestBidder_returnsFalse() {
        Bid bid = new Bid();
        bid.setUser(seller);
        when(bidRepository.findHighestBidByItemId(10L)).thenReturn(Optional.of(bid));

        assertFalse(bidService.isUserWinning(10L, 2L));
    }

    @Test
    void isUserWinning_noBids_returnsFalse() {
        when(bidRepository.findHighestBidByItemId(10L)).thenReturn(Optional.empty());

        assertFalse(bidService.isUserWinning(10L, 2L));
    }

    // -----------------------------------------------------------------------
    // WhoisWinning
    // -----------------------------------------------------------------------

    @Test
    void whoisWinning_bidExists_returnsWinnerUsername() {
        Bid bid = new Bid();
        bid.setUser(bidder);
        when(bidRepository.findHighestBidByItemId(10L)).thenReturn(Optional.of(bid));

        assertEquals("bidder", bidService.WhoisWinning(10L));
    }

    @Test
    void whoisWinning_noBids_returnsNull() {
        when(bidRepository.findHighestBidByItemId(10L)).thenReturn(Optional.empty());

        assertNull(bidService.WhoisWinning(10L));
    }
}
