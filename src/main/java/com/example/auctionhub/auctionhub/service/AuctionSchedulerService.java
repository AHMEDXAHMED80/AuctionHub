package com.example.auctionhub.auctionhub.service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.example.auctionhub.auctionhub.events.dto.AuctionEndedEvent;
import com.example.auctionhub.auctionhub.events.producer.AuctionEndedProducer;
import com.example.auctionhub.auctionhub.models.Bid;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.models.Item.ItemStatus;
import com.example.auctionhub.auctionhub.repository.ItemRepository;
import com.example.auctionhub.auctionhub.repository.BidRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AuctionSchedulerService {
    private final ItemRepository itemRepository;
    private final BidRepository bidRepository;
    private final AuctionEndedProducer auctionEndedProducer;

    public AuctionSchedulerService(ItemRepository itemRepository, BidRepository bidRepository, AuctionEndedProducer auctionEndedProducer) {
        this.itemRepository = itemRepository;
        this.bidRepository = bidRepository;
        this.auctionEndedProducer = auctionEndedProducer;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void processEndedAuctions(){
        log.info("Processing ended auctions");
        
        List<Item> endedItems= itemRepository.findByStatusAndEndDateBeforNow(Item.ItemStatus.ACTIVE);
        if (endedItems == null || endedItems.isEmpty()) {
            log.info("No ended auctions found");
            return;    
        }

        List<Long> itemIds = endedItems.stream().map(Item::getId).toList();
        log.info("Found {} ended auctions to process", endedItems.size());
        itemRepository.updateItemsStatusBatch(itemIds, ItemStatus.PROCESSING);
        log.info("Updated {} items status to PROCESSING", itemIds.size());
        Map<Long, Bid> highestBids = bidRepository.findHighestBidsByItemIds(itemIds).stream()
                                                    .collect(Collectors.toMap(
                                                        bid -> bid.getItem().getId(), 
                                                        bid -> bid));
                                                
        log.info("Found {} highest bids for {} items", highestBids.size(), itemIds.size());

        for(Item item : endedItems){
            Bid itemHighestBid = highestBids.get(item.getId());
            sendAuctionEndedEvent(item, itemHighestBid);
            log.info("Sent AuctionEndedEvent for item {}", item.getId());
            
        }
    }

    /**
     * Helper method to create and send AuctionEndedEvent.
     */
    private void sendAuctionEndedEvent(Item item, Bid winnerBid) {
        try {
            AuctionEndedEvent auctionEndedEvent = new AuctionEndedEvent(
                item.getId(),
                item.getTitle(),
                item.getSeller().getId(),
                winnerBid != null ? winnerBid.getBidAmount() : null,
                winnerBid != null ? winnerBid.getUser().getId() : null,
                winnerBid != null ? winnerBid.getUser().getUsername() : null,
                item.getStatus(),
                item.getEndDate(),
                item.getBids().size() > 0
            );
            auctionEndedProducer.sendAuctionEndedEvent(auctionEndedEvent);
        } catch (Exception e) {
            log.error("Failed to send AuctionEndedEvent for item {}: {}", item.getId(), e.getMessage(), e);
        }

    }

    
}
