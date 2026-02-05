package com.example.auctionhub.auctionhub.repository;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.models.Item.ItemStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
    Optional<Item> findById(Long id);

    // Find all items by seller's user ID
    java.util.List<Item> findAllBySellerId(Long sellerId);

    @Query("Select i from Item i WHERE i.status=:status AND i.endDate < CURRENT_TIMESTAMP")
    List<Item> findByStatusAndEndDateBeforNow(Item.ItemStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE Item i SET i.status = :status WHERE i.id IN :itemIds")
    int updateItemsStatusBatch(@Param("itemIds") List<Long> itemIds, @Param("status") ItemStatus status);

    


    
}