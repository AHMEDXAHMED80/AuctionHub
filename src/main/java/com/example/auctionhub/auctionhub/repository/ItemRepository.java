package com.example.auctionhub.auctionhub.repository;
import com.example.auctionhub.auctionhub.models.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
    Optional<Item> findById(Long id);

    // Find all items by seller's user ID
    java.util.List<Item> findAllBySellerId(Long sellerId);

    @Query("Select i from Item i WHERE i.status=:status AND i.endDate < CURRENT_TIMESTAMP")
    List<Item> findByStatusAndEndDateBeforNow(Item.ItemStatus status);

    


    
}