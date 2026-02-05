package com.example.auctionhub.auctionhub.service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.example.auctionhub.auctionhub.dto.BidResponse;
import com.example.auctionhub.auctionhub.dto.ItemBidderResponse;
import com.example.auctionhub.auctionhub.dto.UserBidHistoryResponse;
import com.example.auctionhub.auctionhub.events.dto.BidEvent;
import com.example.auctionhub.auctionhub.events.producer.BidEventProducer;
import com.example.auctionhub.auctionhub.mapper.BidMapper;
import com.example.auctionhub.auctionhub.mapper.UserBidHistoryMapper;
import com.example.auctionhub.auctionhub.models.Bid;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.models.Wallet;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.models.Bid.bidStatus;
import com.example.auctionhub.auctionhub.models.Item.ItemStatus;
import com.example.auctionhub.auctionhub.repository.BidRepository;
import com.example.auctionhub.auctionhub.repository.ItemRepository;
import com.example.auctionhub.auctionhub.security.SecurityUtils;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing bidding operations, including placing bids, event publishing, and bid mapping.
 * Handles bid validation, persistence, and user wallet checks.
 */
@Slf4j
@Service
public class BidService {

    /**
     * Repository for bid persistence operations.
     */
    private final BidRepository bidRepository;

    /**
     * Repository for item persistence operations.
     */
    private final ItemRepository itemRepository;

    /**
     * Producer for publishing bid events.
     */
    private final BidEventProducer bidEventProducer;

    /**
     * Mapper for converting Bid entities to response DTOs.
     */
    private final BidMapper bidMapper;

    /**
     * Mapper for converting Item entities for bidders.
     */
    private final com.example.auctionhub.auctionhub.mapper.ItemBidderMapper itemBidderMapper;

    /**
     * Service for wallet operations.
     */
    private final WalletService walletService;

    
    
    /**
     * Mapper for converting Bid history to DTOs.
     */
    private final UserBidHistoryMapper userBidHistoryMapper;
    /**
     * Service for item image operations.
     */
    private final ItemImageService itemImageService;

    /**
     * Constructs a BidService with required dependencies.
     *
     * @param itemRepository         Repository for Item persistence
     * @param bidEventProducer       Producer for bid events
     * @param bidRepository          Repository for Bid persistence
     * @param bidMapper              Mapper for Bid to DTOs
     * @param itemBidderMapper       Mapper for ItemBidder responses
     * @param walletService          Service for wallet operations
     * @param userBidHistoryMapper   Mapper for user bid history DTOs
     * @param itemImageService       Service for item image operations
     */
    public BidService(ItemRepository itemRepository, BidEventProducer bidEventProducer, BidRepository bidRepository, BidMapper bidMapper, com.example.auctionhub.auctionhub.mapper.ItemBidderMapper itemBidderMapper, WalletService walletService, UserBidHistoryMapper userBidHistoryMapper, ItemImageService itemImageService) {
        this.itemRepository = itemRepository;
        this.bidEventProducer = bidEventProducer;
        this.bidRepository = bidRepository;
        this.bidMapper = bidMapper;
        this.itemBidderMapper = itemBidderMapper;
        this.walletService = walletService;
        this.userBidHistoryMapper = userBidHistoryMapper;
        this.itemImageService = itemImageService;
    }

    /**
     * Places a bid on an item for the authenticated user, validating wallet and publishing events.
     *
     * @param itemId    ID of the item to bid on
     * @param bidAmount Amount to bid
     * @throws RuntimeException if user has no wallet
     */
    @Transactional
    public void placeBid(Long itemId, BigDecimal bidAmount) {
        
        Item item = itemRepository.findById(itemId)
                            .orElseThrow(() -> new RuntimeException("Item not found with id: " + itemId));
        if (item.getStatus() != ItemStatus.ACTIVE) {
            throw new IllegalStateException("Item must be ACTIVE to perform this action");
        }
        int maxRetries = 3;
        int attempt= 0;
        while (attempt < maxRetries) {
        try {
            attempt++;
            log.info("Attempting to place bid on item {} with amount {}", itemId, bidAmount);

            User user = SecurityUtils.getAuthenticatedUserOrThrow();
            Wallet wallet = user.getWallet();
            if (wallet == null) {
                log.error("User {} does not have a wallet", user.getId());
                throw new RuntimeException("You don't have a wallet. Create a wallet first");
            }            

            // Validate bid
            log.info("Validating bid for user {} on item {}", user.getId(), itemId);
            validateBid(item, bidAmount, user);
            handleBidWalletTransactions(itemId, user, bidAmount);

            log.info("creating the bid");

            Bid bid = new Bid();
            bid.setItem(item);
            bid.setStatus(bidStatus.ACTIVE);
            bid.setUser(user);
            bid.setWallet(wallet);
            bid.setBidAmount(bidAmount);
            bid.setCurrentHighestBid(true);
            bidRepository.save(bid);

            log.info("bid created and saved in the database ", bid);
            
            log.info("Placing bid for user {} on item {} with amount {}", user.getId(), itemId, bidAmount);
            item.setCurrentHighestBid(bidAmount);
            itemRepository.save(item);

            // Publish bid event to Kafka
            BidEvent bidEvent = new BidEvent(
                itemId,
                user.getId(),
                user.getUsername(),
                bidAmount,
                LocalDateTime.now(),
                item.getTitle()
            );

            bidEventProducer.publishBidEvent(bidEvent);
            log.info("Bid placed successfully and event sent to Kafka: {}", bidEvent);

            return;
            
        } catch (OptimisticLockException e) {
            log.warn("Concurrent update detected on attempt {} - retrying with fresh data", attempt);
            
            if (attempt >= maxRetries) {
                throw new RuntimeException("Unable to place bid due to high activity. Please try again.");
            }
            
            try {
                Long sleepTime = 50L * attempt;
                log.debug("Sleeping {}ms before retry", sleepTime);
                Thread.sleep(sleepTime);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Bid placement interrupted");
            }
        } catch (RuntimeException e) {
            log.error("Unexpected error while placing bid on item {}: {}", itemId, e.getMessage(), e);
            throw e;
        }
    }
        
    throw new RuntimeException("Failed to place bid after " + maxRetries + " attempts");
    }

    /**
     * Retrieves the complete bid history for a specific item.
     * Returns all bids placed on the item ordered by bid time.
     *
     * @param itemId ID of the item to retrieve bid history for
     * @return List of bid responses containing bid details
     */
    public List<BidResponse> getBidhistory(Long itemId) {
        log.debug("Retrieving bid history for item {}", itemId);
        List<Bid> bids = bidRepository.findByItemId(itemId);
        log.info("Found {} bids for item {}", bids.size(), itemId);
        return bids.stream().map(bidMapper::toBidResponse).toList();
    }

    /**
     * Retrieves all bids placed by the currently authenticated user.
     * Useful for displaying user's bidding activity and history.
     *
     * @return List of bid responses for the authenticated user
     */
    public List<BidResponse> getUserBids() {
        User user = SecurityUtils.getAuthenticatedUserOrThrow();
        Long userId = user.getId();
        log.debug("Retrieving all bids for user {}", userId);
        List<Bid> bids = bidRepository.findByUserId(userId);
        log.info("Found {} bids for user {}", bids.size(), userId);
        return bids.stream().map(bidMapper::toBidResponse).toList();
    }

    /**
     * Gets the current highest bid amount for a specific item.
     * Returns zero if no bids have been placed yet.
     *
     * @param itemId ID of the item to check
     * @return The highest bid amount, or BigDecimal.ZERO if no bids exist
     */
    public BigDecimal getHighestBid(Long itemId) {
        log.debug("Retrieving highest bid for item {}", itemId);
        Bid highestBid = bidRepository.findHighestBidByItemId(itemId).orElse(null);
        BigDecimal bidAmount = highestBid != null ? highestBid.getBidAmount() : BigDecimal.ZERO;
        log.info("Highest bid for item {}: {}", itemId, bidAmount);
        return bidAmount;
    }

    /**
     * Checks if a specific user is currently the highest bidder on an item.
     *
     * @param itemId ID of the item to check
     * @param userId ID of the user to check
     * @return true if the user is the current highest bidder, false otherwise
     */
    public boolean isUserWinning(Long itemId, Long userId) {
        log.debug("Checking if user {} is winning item {}", userId, itemId);
        Bid highestBid = bidRepository.findHighestBidByItemId(itemId).orElse(null);
        boolean isWinning = highestBid != null && highestBid.getUser().getId().equals(userId);
        log.debug("User {} winning status for item {}: {}", userId, itemId, isWinning);
        return isWinning;
    }

    /**
     * Gets the username of the current highest bidder for an item.
     *
     * @param itemId ID of the item to check
     * @return Username of the current highest bidder, or null if no bids exist
     */
    public String WhoisWinning(Long itemId) {
        log.debug("Retrieving current winner for item {}", itemId);
        Bid highestBid = bidRepository.findHighestBidByItemId(itemId).orElse(null);
        if (highestBid == null) {
            log.warn("No bids found for item {}", itemId);
            return null;
        }
        String username = highestBid.getUser().getUsername();
        log.info("Current winner for item {}: {}", itemId, username);
        return username;
    }

    /**
     * Retrieves all active items that the authenticated user has bid on.
     * Includes bid counts and current highest bid for each item.
     * Only returns items with ACTIVE status (ongoing auctions).
     *
     * @return List of item responses with bidding information
     */
    public List<ItemBidderResponse> getUserActiveBids() {
       User user = SecurityUtils.getAuthenticatedUserOrThrow();
       log.debug("Retrieving active bids for user {}", user.getId());
       
       // Fetch all active items the user has bid on
       List<Item> activeItems = bidRepository.findUserActiveBids(user.getId(), ItemStatus.ACTIVE);
       log.info("Found {} active items with bids from user {}", activeItems.size(), user.getId());
       
       if (activeItems.isEmpty()) {
           return List.of();
       }
       
       List<Long> itemIds = activeItems.stream().map(Item::getId).toList();
       // Batch fetch bid counts to avoid N+1 queries
       Map<Long, Long> bidCountsMap = getBidCountsForItems(itemIds);
        
       return activeItems.stream().map(item -> {
            // Map item to response
            ItemBidderResponse response = itemBidderMapper.toItemBidderResponse(item);
            
            // Set current highest bid
            if (item.getCurrentHighestBid() != null) {
                response.setCurrentHighestBid(item.getCurrentHighestBid());
            }

            // Set total bid count for this item
            Long bidCount = bidCountsMap.getOrDefault(item.getId(), 0L);
            response.setTotalBids(bidCount);
            return response;
        }).toList();
    }

    /**
     * Retrieves the complete bid history for the authenticated user.
     * Shows all items the user has bid on, with their status (won/lost),
     * final bid amount, and item image. Uses batch fetching to optimize performance.
     *
     * @return List of user bid history responses with item details and outcome
     */
    public List<UserBidHistoryResponse> getUserunActiveBids() {
        User user = SecurityUtils.getAuthenticatedUserOrThrow();
        log.debug("Retrieving bid history for user {}", user.getId());
        
        // Get all items the user has bid on
        List<Item> userHistoryBidsItems = bidRepository.findUserBidHistoryItems(user.getId());
        log.info("Found {} items in bid history for user {}", userHistoryBidsItems.size(), user.getId());
        
        if (userHistoryBidsItems.isEmpty()) {
            log.debug("No bid history found for user {}", user.getId());
            return List.of();
        }
        
        List<Long> userHistoryBidsItemId = userHistoryBidsItems.stream().map(Item::getId).toList();
        
        // Batch fetch to avoid N+1 problem
        log.debug("Batch fetching images and highest bids for {} items", userHistoryBidsItemId.size());
        Map<Long, String> urlsForImagesFirstIndex = itemImageService.getFirstImageIndexForAllItems(userHistoryBidsItemId);
        List<Bid> highestBids = bidRepository.findHighestBidsByItemIds(userHistoryBidsItemId);
        Map<Long, Bid> highestBidsMap = highestBids.stream()
            .collect(Collectors.toMap(bid -> bid.getItem().getId(), bid -> bid));

        return userHistoryBidsItems.stream().map(item -> {
            Long itemId = item.getId();
            String imageUrl = urlsForImagesFirstIndex.getOrDefault(itemId, "");
            String itemTitle = item.getTitle();
            BigDecimal lastBidAmount = item.getCurrentHighestBid();
            LocalDateTime endedAt = item.getEndDate();
            
            // Determine if user won or lost by checking if they are the highest bidder
            Bid highestBid = highestBidsMap.get(itemId);
            bidStatus status = (highestBid != null && highestBid.getUser().getId().equals(user.getId())) 
                ? bidStatus.ACTIVE  // User is winning
                : bidStatus.LOST;   // User was outbid

            return userBidHistoryMapper.toUserBidHistoryResponse(
                itemId, 
                itemTitle, 
                imageUrl, 
                lastBidAmount.toString(), 
                status,
                endedAt
            );
        }).toList();
    }

    private void validateBid(Item item, BigDecimal bidAmount, User user) {
        try {
            log.debug("Validating bid for user {} on item {}", user.getId(), item.getId());
            
            // Check if auction has ended
            if (item.getEndDate().isBefore(LocalDateTime.now())) {
                log.warn("Bid rejected: Item {} has expired", item.getId());
                throw new RuntimeException("Auction has ended");
            }

            // Check if user is the seller
            if (user.getId().equals(item.getSeller().getId())) {
                log.warn("Bid rejected: User {} tried to bid on their own item {}", user.getId(), item.getId());
                throw new RuntimeException("You cannot bid on your own item");
            }

            BigDecimal minimumBid = item.getCurrentHighestBid();
            BigDecimal availableBalance = user.getWallet().getAvailableBalance();

            // Check if bid is higher than current highest bid
            if (bidAmount.compareTo(minimumBid) <= 0) {
                log.warn("Bid rejected: Amount {} is not higher than current bid {}", bidAmount, minimumBid);
                throw new RuntimeException("Bid must be higher than " + minimumBid);
            }

            // Check if user has sufficient balance
            if (availableBalance.compareTo(bidAmount) < 0) {
                log.warn("Bid rejected: User {} has insufficient balance. Available: {}, Required: {}", 
                    user.getId(), availableBalance, bidAmount);
                throw new RuntimeException("Insufficient balance. Available: " + availableBalance + ", Required: " + bidAmount);
            }

            // Check if user has POSTER role (cannot bid)
            if (user.getRole().equals(User.roles.POSTER)) {
                 log.warn("Bid rejected: User {} role {}. Cannot bid on items", 
                user.getId(), user.getRole());
                throw new RuntimeException("You are not allowed to bid on items");
            }
            
            log.debug("Bid validation successful for user {} on item {}", user.getId(), item.getId());
            
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during bid validation: {}", e.getMessage(), e);
            throw new RuntimeException("Bid validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves bid counts for multiple items in a single batch query.
     * Optimizes performance by avoiding N+1 queries when fetching counts for multiple items.
     *
     * @param itemIds List of item IDs to get bid counts for
     * @return Map of item ID to bid count
     */
    private Map<Long, Long> getBidCountsForItems(List<Long> itemIds) {
        if (itemIds.isEmpty()) {
            log.debug("No item IDs provided for bid count lookup");
            return Map.of();
        }

        log.debug("Fetching bid counts for {} items", itemIds.size());
        List<Object[]> result = bidRepository.countByItemId(itemIds);

        // Convert query result to map: itemId -> bidCount
        return result.stream()
            .collect(Collectors.toMap(
                    row -> (Long) row[0],  // Item ID
                    row -> (Long) row[1]   // Bid count
            ));
    }


    /**
     * Handles wallet transactions when a new bid is placed.
     * - Releases frozen funds from the previous highest bidder's wallet
     * - Freezes the new bid amount from the new bidder's wallet
     * - Updates bid statuses (previous bid marked as LOST)
     *
     * @param itemId ID of the item being bid on
     * @param newBidder User placing the new bid
     * @param NewAmount Amount of the new bid to freeze
     */
    private void handleBidWalletTransactions(Long itemId, User newBidder, BigDecimal newAmount) {
        try {
            log.debug("Processing wallet transactions for new bid on item {}", itemId);

            // Find the previous highest bid
            Bid prevHighestBid = bidRepository.findHighestBidByItemId(itemId).orElse(null);

            // Step 1: Return frozen funds to previous bidder's available balance (if outbid)
            if (prevHighestBid != null) {
                User prevUser = prevHighestBid.getUser();
                log.info("Returning frozen funds to previous bidder {} on item {}", prevUser.getId(), itemId);

                // Unfreeze and return funds to previous bidder's available balance
                walletService.deductFromFrozenBalance(prevUser.getId(), prevHighestBid.getBidAmount());

                // Mark previous bid as lost
                prevHighestBid.setStatus(bidStatus.LOST);
                prevHighestBid.setCurrentHighestBid(false);
                bidRepository.save(prevHighestBid);

                log.info("Returned {} to previous bidder {}'s available balance",
                    prevHighestBid.getBidAmount(), prevUser.getId());
            } else {
                log.debug("No previous bid exists for item {}, skipping fund release", itemId);
            }

            // Step 2: Freeze funds from new bidder (move from available to frozen)
            log.info("Freezing {} from user {} wallet for bid on item {}", newAmount, newBidder.getId(), itemId);
            walletService.addToFrozenBalance(newBidder.getId(), newAmount);

            log.info("Funds frozen successfully for user {}", newBidder.getId());
        } catch (Exception e) {
            log.error("Error handling wallet transactions for item {}: {}", itemId, e.getMessage(), e);
            throw new RuntimeException("Failed to process wallet transactions for bid on item " + itemId, e);
        }
    }

    


}
