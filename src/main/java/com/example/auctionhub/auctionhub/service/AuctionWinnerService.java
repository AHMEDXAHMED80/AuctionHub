package com.example.auctionhub.auctionhub.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.auctionhub.auctionhub.models.AuctionWinner;
import com.example.auctionhub.auctionhub.models.Bid;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.models.AuctionWinner.WinnerStatus;
import com.example.auctionhub.auctionhub.models.Item.ItemStatus;
import com.example.auctionhub.auctionhub.repository.BidRepository;
import com.example.auctionhub.auctionhub.repository.ItemRepository;
import com.example.auctionhub.auctionhub.repository.AuctionWinnerRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for handling auction winner operations after auctions end.
 * Processes winning bids and updates item statuses.
 */
@Slf4j
@Service
public class AuctionWinnerService {
    private final BidRepository bidRepository;
    private final ItemRepository itemRepository;
    private final AuctionWinnerRepository auctionWinnerRepository;
    private final WalletService walletService;

    /**
     * Constructs AuctionWinnerService with required dependencies.
     *
     * @param bidRepository  Repository for bid operations
     * @param itemRepository Repository for item operations
     * @param auctionWinnerRepository Repository for auction winner operations
     * @param walletService Service for wallet operations
     */
    public AuctionWinnerService(BidRepository bidRepository, ItemRepository itemRepository, AuctionWinnerRepository auctionWinnerRepository, WalletService walletService) {
        this.bidRepository = bidRepository;
        this.itemRepository = itemRepository;
        this.auctionWinnerRepository = auctionWinnerRepository;
        this.walletService = walletService;
    }
    
    /**
     * Processes a single ended auction.
     * Marks winning bid as WON and item as SOLD, or marks item as EXPIRED if no bids.
     *
     * @param item The item whose auction has ended
     */
    @Transactional
    public void processEndedAuctions(Item item) {
        try {
            log.info("Processing ended auction for item {}", item.getId());
            Bid winningBid = bidRepository.findHighestBidByItemId(item.getId()).orElse(null);
            if (winningBid != null) {
                log.info("Found winning bid for item {}: {}", item.getId(), winningBid.getId());
                winningBid.setStatus(Bid.bidStatus.WON);
                bidRepository.save(winningBid);

                item.setStatus(Item.ItemStatus.SOLD);
                itemRepository.save(item);

                transferFrozenFundsToSellerWallet(item, winningBid);
                log.info("Item {} has been marked as SOLD", item.getId());
            } else {
                log.info("No winning bid found for item {}", item.getId());
                item.setStatus(ItemStatus.EXPIRED);
                itemRepository.save(item);
                log.info("Item {} has expired", item.getId());
            }
        } catch (Exception e) {
            log.error("Error processing ended auction for item {}: {}", item.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to process ended auction for item " + item.getId(), e);
        }
    }

    /**
     * Transfers frozen funds from winner to seller wallet.
     *
     * @param item The sold item
     * @param winnerBid The winning bid
     */
    private void transferFrozenFundsToSellerWallet(Item item, Bid winnerBid) {
    try {
        log.info("Transferring funds to seller wallet for item {}", item.getId());
        User winnerBidder = winnerBid.getUser();
        BigDecimal bidAmount = winnerBid.getBidAmount();
        User seller = item.getSeller();
        
        Boolean isTransferred = walletService.transferFrozenToSellerBalance(
            winnerBidder.getId(), seller.getId(), bidAmount
        );
        
        if (isTransferred) {
            log.info("Transfer frozen funds uccessful for item {}", item.getId());
            setAuctionWinnerPaidFund(winnerBidder, bidAmount, item, winnerBid);
        } else {
            log.warn("Transfer frozen funds failed for item {}", item.getId());
            setAuctionWinnerTransferFailed(winnerBidder, bidAmount, item, winnerBid);
        }
        
        
    } catch (Exception e) {
        log.error("Error transferring funds to seller wallet for item {}: {}", 
                 item.getId(), e.getMessage(), e);
        throw new RuntimeException("Failed to transfer funds to seller wallet for item " 
                                  + item.getId(), e);
    }
}
    /**
     * Creates and saves auction winner record.
     *
     * @param winnerUser The winning user
     * @param finalBidAmount The final bid amount
     * @param item The won item
     * @param bid The winning bid
     */
    private void setAuctionWinnerPaidFund(User winnerUser, BigDecimal finalBidAmount, Item item, Bid bid) {
        try {
            AuctionWinner auctionWinner = new AuctionWinner();
            auctionWinner.setWinner(winnerUser);
            auctionWinner.setFinalBidAmount(finalBidAmount);
            auctionWinner.setItem(item);
            auctionWinner.setWinningBid(bid);
            auctionWinner.setStatus(WinnerStatus.FUNDS_TRANSFERRED);
            auctionWinner.setWonAt(item.getEndDate());
            auctionWinner.setFundsTransferredAt(LocalDateTime.now());
            auctionWinnerRepository.save(auctionWinner);
            log.info("Saving auction winner for item {}", item.getId());
        } catch (Exception e) {
            log.error("Error saving auction winner record for item {}: {}", item.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to save auction winner record for item " + item.getId(), e);
        }
    }


    /**
     * Creates and saves auction winner record with TRANSFER_FAILED status.
     *
     * @param winnerUser The winning user
     * @param finalBidAmount The final bid amount
     * @param item The won item
     * @param bid The winning bid
     */
    private void setAuctionWinnerTransferFailed(User winnerUser, BigDecimal finalBidAmount, Item item, Bid bid) {
        try {
            AuctionWinner auctionWinner = new AuctionWinner();
            auctionWinner.setWinner(winnerUser);
            auctionWinner.setFinalBidAmount(finalBidAmount);
            auctionWinner.setItem(item);
            auctionWinner.setWinningBid(bid);
            auctionWinner.setStatus(WinnerStatus.TRANSFER_FAILED);
            auctionWinner.setWonAt(item.getEndDate());
            auctionWinner.setFundsTransferredAt(LocalDateTime.now());
            auctionWinnerRepository.save(auctionWinner);
            log.info("Saved auction winner with TRANSFER_FAILED status for item {}", item.getId());
        } catch (Exception e) {
            log.error("Error saving failed transfer record for item {}: {}", item.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to save transfer failed record for item " + item.getId(), e);
        }
    }


}
