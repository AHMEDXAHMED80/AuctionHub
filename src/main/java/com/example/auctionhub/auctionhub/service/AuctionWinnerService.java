package com.example.auctionhub.auctionhub.service;

import java.math.BigDecimal;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.auctionhub.auctionhub.models.AuctionWinner;
import com.example.auctionhub.auctionhub.models.Bid;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.models.Wallet;
import com.example.auctionhub.auctionhub.models.AuctionWinner.WinnerStatus;
import com.example.auctionhub.auctionhub.models.Item.ItemStatus;
import com.example.auctionhub.auctionhub.repository.BidRepository;
import com.example.auctionhub.auctionhub.repository.ItemRepository;
import com.example.auctionhub.auctionhub.repository.WalletRepository;
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
    private final WalletRepository walletRepository;
    private final AuctionWinnerRepository auctionWinnerRepository;
    
    /**
     * Constructs AuctionWinnerService with required dependencies.
     *
     * @param bidRepository  Repository for bid operations
     * @param itemRepository Repository for item operations
     * @param walletRepository Repository for wallet operations
     * @param auctionWinnerRepository Repository for auction winner operations
     */
    public AuctionWinnerService(BidRepository bidRepository, ItemRepository itemRepository, WalletRepository walletRepository, AuctionWinnerRepository auctionWinnerRepository) {
        this.bidRepository = bidRepository;
        this.itemRepository = itemRepository;
        this.walletRepository = walletRepository;
        this.auctionWinnerRepository = auctionWinnerRepository;
    }
    
    /**
     * Processes a single ended auction.
     * Marks winning bid as WON and item as SOLD, or marks item as EXPIRED if no bids.
     *
     * @param item The item whose auction has ended
     */
    @Transactional
    public void processEndedAuctions(Item item) {
        log.info("Processing ended auction for item {}", item.getId());
        Bid winningBid = bidRepository.findHighestBidByItemId(item.getId()).orElse(null);
        if (winningBid != null) {
            log.info("Found winning bid for item {}: {}", item.getId(), winningBid.getId());
            winningBid.setStatus(Bid.bidStatus.WON);
            bidRepository.save(winningBid);
            
            item.setStatus(Item.ItemStatus.SOLD);
            itemRepository.save(item);

            transferFundsToSellerWallet(item, winningBid);
            log.info("Item {} has been marked as SOLD", item.getId());
        }

        else{
            
            log.info("No winning bid found for item {}", item.getId());
            item.setStatus(ItemStatus.EXPIRED);
            itemRepository.save(item);
            log.info("Item {} has expired", item.getId());
        }
    }

    /**
     * Transfers frozen funds from winner to seller wallet.
     *
     * @param item The sold item
     * @param winnerBid The winning bid
     */
    private void transferFundsToSellerWallet(Item item, Bid winnerBid) {            
        log.info("Transferring funds to seller wallet for item {}", item.getId());
        User winnerBidder = winnerBid.getUser();
        BigDecimal bidAmount = winnerBid.getBidAmount();
        Wallet winnerBidderWallet = winnerBidder.getWallet();
        User seller = item.getSeller();
        Wallet sellerWallet = seller.getWallet();
        
        // Validate frozen balance
        if (winnerBidderWallet.getFrozenBalance().compareTo(bidAmount) < 0) {
            log.error("Insufficient frozen balance for user {}. Required: {}, Available: {}",
                winnerBidder.getId(), bidAmount, winnerBidderWallet.getFrozenBalance());
                setAuctionWinnerUnPaidFund(winnerBidder, bidAmount, item, winnerBid);
        }
        
        winnerBidderWallet.setFrozenBalance(winnerBidderWallet.getFrozenBalance().subtract(bidAmount));
        sellerWallet.setAvailableBalance(sellerWallet.getAvailableBalance().add(bidAmount));
        walletRepository.saveAll(Arrays.asList(winnerBidderWallet, sellerWallet));
        log.info("Funds transferred successfully. Seller wallet: {}", sellerWallet.getAvailableBalance());

        setAuctionWinnerPaidFund(winnerBidder, bidAmount, item, winnerBid);
        
    }

    /**
     * Creates and saves auction winner record.
     *
     * @param winnerUser The winning user
     * @param finalBidAmount The final bid amount
     * @param item The won item
     * @param bid The winning bid
     */
    private void setAuctionWinnerPaidFund(User winnerUser, BigDecimal finalBidAmount, Item item, Bid bid){
       AuctionWinner auctionWinner = new AuctionWinner();
       auctionWinner.setWinner(winnerUser);
       auctionWinner.setFinalBidAmount(finalBidAmount);
       auctionWinner.setItem(item);
       auctionWinner.setWinningBid(bid);
       auctionWinner.setStatus(WinnerStatus.FUNDS_TRANSFERRED);
       auctionWinner.setWonAt(item.getEndDate());
       auctionWinnerRepository.save(auctionWinner);

       log.info("Saving auction winner for item {}", item.getId());       
    }


    /**
     * Creates and saves auction winner record with TRANSFER_FAILED status.
     *
     * @param winnerUser The winning user
     * @param finalBidAmount The final bid amount
     * @param item The won item
     * @param bid The winning bid
     */
    private void setAuctionWinnerTransferFailed(User winnerUser, BigDecimal finalBidAmount, Item item, Bid bid){
       AuctionWinner auctionWinner = new AuctionWinner();
       auctionWinner.setWinner(winnerUser);
       auctionWinner.setFinalBidAmount(finalBidAmount);
       auctionWinner.setItem(item);
       auctionWinner.setWinningBid(bid);
       auctionWinner.setStatus(WinnerStatus.TRANSFER_FAILED);
       auctionWinner.setWonAt(item.getEndDate());
       auctionWinnerRepository.save(auctionWinner);

       log.info("Saved auction winner with TRANSFER_FAILED status for item {}", item.getId());       
    }


}
