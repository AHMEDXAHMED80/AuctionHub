package com.example.auctionhub.auctionhub.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.auctionhub.auctionhub.models.Bid;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.models.Item.ItemStatus;
import com.example.auctionhub.auctionhub.repository.BidRepository;
import com.example.auctionhub.auctionhub.repository.ItemRepository;
import com.example.auctionhub.auctionhub.repository.WalletRepository;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AuctionSchedulerService {
    private final ItemRepository itemRepository;
    private final AuctionWinnerService auctionWinnerService;


    public AuctionSchedulerService(ItemRepository itemRepository, AuctionWinnerService auctionWinnerService) {
        this.itemRepository = itemRepository;
        this.auctionWinnerService = auctionWinnerService;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void processEndedAuctions(){
        log.info("Processing ended auctions");
        
        List<Item> endedItems= itemRepository.findByStatusAndEndDateBeforNow(Item.ItemStatus.ACTIVE);
        log.info("Found {} ended auctions to process", endedItems.size());

        for(Item item : endedItems){
            log.info("Processing ended auction for item {}", item.getId());
            auctionWinnerService.processEndedAuctions(item);
            
        }
    }

    
}
