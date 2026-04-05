package com.example.auctionhub.auctionhub.service;

import com.example.auctionhub.auctionhub.models.AuctionWinner;
import com.example.auctionhub.auctionhub.models.AuctionWinner.WinnerStatus;
import com.example.auctionhub.auctionhub.models.Bid;
import com.example.auctionhub.auctionhub.models.Bid.bidStatus;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.models.Item.ItemStatus;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.models.Wallet;
import com.example.auctionhub.auctionhub.repository.AuctionWinnerRepository;
import com.example.auctionhub.auctionhub.repository.BidRepository;
import com.example.auctionhub.auctionhub.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionWinnerServiceTest {

    @Mock private BidRepository bidRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private AuctionWinnerRepository auctionWinnerRepository;
    @Mock private WalletService walletService;

    @InjectMocks private AuctionWinnerService auctionWinnerService;

    private User seller;
    private User winner;
    private Item item;
    private Bid winningBid;

    @BeforeEach
    void setUp() {
        seller = new User();
        seller.setId(10L);
        seller.setUsername("seller");

        Wallet sellerWallet = new Wallet();
        sellerWallet.setAvailableBalance(BigDecimal.ZERO);
        seller.setWallet(sellerWallet);

        winner = new User();
        winner.setId(20L);
        winner.setUsername("winner");

        Wallet winnerWallet = new Wallet();
        winnerWallet.setFrozenBalance(new BigDecimal("350.00"));
        winner.setWallet(winnerWallet);

        item = new Item();
        item.setId(100L);
        item.setTitle("Vintage Watch");
        item.setStatus(ItemStatus.ACTIVE);
        item.setSeller(seller);
        item.setEndDate(LocalDateTime.now().minusMinutes(5));

        winningBid = new Bid();
        winningBid.setId(1L);
        winningBid.setUser(winner);
        winningBid.setBidAmount(new BigDecimal("350.00"));
        winningBid.setStatus(bidStatus.ACTIVE);
        winningBid.setCurrentHighestBid(true);
        winningBid.setItem(item);
    }

    // -----------------------------------------------------------------------
    // With winning bid — SOLD path
    // -----------------------------------------------------------------------

    @Test
    void process_withWinningBid_marksBidStatusWon() {
        when(bidRepository.findHighestBidByItemId(100L)).thenReturn(Optional.of(winningBid));
        when(bidRepository.save(any())).thenReturn(winningBid);
        when(itemRepository.save(any())).thenReturn(item);
        when(walletService.transferFrozenToSellerBalance(any(), any(), any())).thenReturn(true);
        when(auctionWinnerRepository.save(any())).thenReturn(new AuctionWinner());

        auctionWinnerService.processEndedAuctions(item);

        assertEquals(bidStatus.WON, winningBid.getStatus());
        verify(bidRepository).save(winningBid);
    }

    @Test
    void process_withWinningBid_marksItemSold() {
        when(bidRepository.findHighestBidByItemId(100L)).thenReturn(Optional.of(winningBid));
        when(bidRepository.save(any())).thenReturn(winningBid);
        when(itemRepository.save(any())).thenReturn(item);
        when(walletService.transferFrozenToSellerBalance(any(), any(), any())).thenReturn(true);
        when(auctionWinnerRepository.save(any())).thenReturn(new AuctionWinner());

        auctionWinnerService.processEndedAuctions(item);

        assertEquals(ItemStatus.SOLD, item.getStatus());
        verify(itemRepository).save(item);
    }

    @Test
    void process_withWinningBid_transfersFrozenFundsToSeller() {
        when(bidRepository.findHighestBidByItemId(100L)).thenReturn(Optional.of(winningBid));
        when(bidRepository.save(any())).thenReturn(winningBid);
        when(itemRepository.save(any())).thenReturn(item);
        when(walletService.transferFrozenToSellerBalance(any(), any(), any())).thenReturn(true);
        when(auctionWinnerRepository.save(any())).thenReturn(new AuctionWinner());

        auctionWinnerService.processEndedAuctions(item);

        verify(walletService).transferFrozenToSellerBalance(
            eq(20L), eq(10L), eq(new BigDecimal("350.00")));
    }

    @Test
    void process_transferSucceeds_savesWinnerRecordWithFundsTransferredStatus() {
        when(bidRepository.findHighestBidByItemId(100L)).thenReturn(Optional.of(winningBid));
        when(bidRepository.save(any())).thenReturn(winningBid);
        when(itemRepository.save(any())).thenReturn(item);
        when(walletService.transferFrozenToSellerBalance(any(), any(), any())).thenReturn(true);

        ArgumentCaptor<AuctionWinner> captor = ArgumentCaptor.forClass(AuctionWinner.class);
        when(auctionWinnerRepository.save(captor.capture())).thenReturn(new AuctionWinner());

        auctionWinnerService.processEndedAuctions(item);

        AuctionWinner saved = captor.getValue();
        assertEquals(WinnerStatus.FUNDS_TRANSFERRED, saved.getStatus());
        assertEquals(winner, saved.getWinner());
        assertEquals(item, saved.getItem());
        assertEquals(winningBid, saved.getWinningBid());
        assertEquals(new BigDecimal("350.00"), saved.getFinalBidAmount());
    }

    @Test
    void process_transferFails_savesWinnerRecordWithTransferFailedStatus() {
        when(bidRepository.findHighestBidByItemId(100L)).thenReturn(Optional.of(winningBid));
        when(bidRepository.save(any())).thenReturn(winningBid);
        when(itemRepository.save(any())).thenReturn(item);
        when(walletService.transferFrozenToSellerBalance(any(), any(), any())).thenReturn(false);

        ArgumentCaptor<AuctionWinner> captor = ArgumentCaptor.forClass(AuctionWinner.class);
        when(auctionWinnerRepository.save(captor.capture())).thenReturn(new AuctionWinner());

        auctionWinnerService.processEndedAuctions(item);

        assertEquals(WinnerStatus.TRANSFER_FAILED, captor.getValue().getStatus());
    }

    @Test
    void process_withWinningBid_wonAtIsSetToItemEndDate() {
        when(bidRepository.findHighestBidByItemId(100L)).thenReturn(Optional.of(winningBid));
        when(bidRepository.save(any())).thenReturn(winningBid);
        when(itemRepository.save(any())).thenReturn(item);
        when(walletService.transferFrozenToSellerBalance(any(), any(), any())).thenReturn(true);

        ArgumentCaptor<AuctionWinner> captor = ArgumentCaptor.forClass(AuctionWinner.class);
        when(auctionWinnerRepository.save(captor.capture())).thenReturn(new AuctionWinner());

        auctionWinnerService.processEndedAuctions(item);

        assertEquals(item.getEndDate(), captor.getValue().getWonAt());
    }

    // -----------------------------------------------------------------------
    // No bids — EXPIRED path
    // -----------------------------------------------------------------------

    @Test
    void process_noBids_marksItemExpired() {
        when(bidRepository.findHighestBidByItemId(100L)).thenReturn(Optional.empty());
        when(itemRepository.save(any())).thenReturn(item);

        auctionWinnerService.processEndedAuctions(item);

        assertEquals(ItemStatus.EXPIRED, item.getStatus());
        verify(itemRepository).save(item);
    }

    @Test
    void process_noBids_doesNotSaveAuctionWinnerRecord() {
        when(bidRepository.findHighestBidByItemId(100L)).thenReturn(Optional.empty());
        when(itemRepository.save(any())).thenReturn(item);

        auctionWinnerService.processEndedAuctions(item);

        verify(auctionWinnerRepository, never()).save(any());
    }

    @Test
    void process_noBids_doesNotTransferFunds() {
        when(bidRepository.findHighestBidByItemId(100L)).thenReturn(Optional.empty());
        when(itemRepository.save(any())).thenReturn(item);

        auctionWinnerService.processEndedAuctions(item);

        verify(walletService, never()).transferFrozenToSellerBalance(any(), any(), any());
    }

    @Test
    void process_noBids_doesNotMarkAnyBidAsWon() {
        when(bidRepository.findHighestBidByItemId(100L)).thenReturn(Optional.empty());
        when(itemRepository.save(any())).thenReturn(item);

        auctionWinnerService.processEndedAuctions(item);

        verify(bidRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Error handling
    // -----------------------------------------------------------------------

    @Test
    void process_walletTransferThrows_wrapsAndRethrows() {
        when(bidRepository.findHighestBidByItemId(100L)).thenReturn(Optional.of(winningBid));
        when(bidRepository.save(any())).thenReturn(winningBid);
        when(itemRepository.save(any())).thenReturn(item);
        when(walletService.transferFrozenToSellerBalance(any(), any(), any()))
            .thenThrow(new RuntimeException("Wallet service unavailable"));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> auctionWinnerService.processEndedAuctions(item));

        assertTrue(ex.getMessage().contains("Failed to process ended auction"));
    }
}
