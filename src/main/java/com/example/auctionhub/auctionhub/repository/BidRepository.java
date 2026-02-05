package com.example.auctionhub.auctionhub.repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.auctionhub.auctionhub.models.Bid;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.models.User;

import jakarta.transaction.Transactional;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {
    
    // Find all bids for a specific item
    List<Bid> findByItemId(Long itemId);
    
    // Find all bids by a specific user
    List<Bid> findByUserId(Long userId);
    
    // Find all unique users who bid on a specific item (for notifications)
    @Query("SELECT DISTINCT b.user.id FROM Bid b WHERE b.item.id = :itemId AND b.user.id != :excludeUserId AND b.user.id != :higestBidderId")
    List<Long> findDistinctUserIdsByItemId(@Param("itemId") Long itemId, @Param("excludeUserId") Long excludeUserId, @Param("higestBidderId")Long higestBidderId);
    
    // Find highest bid for an item
    @Query("SELECT b FROM Bid b WHERE b.item.id = :itemId ORDER BY b.bidAmount DESC LIMIT 1")
     Optional<Bid> findHighestBidByItemId(@Param("itemId") Long itemId);


    @Query("SELECT B FROM Bid B WHERE B.user.id = :userId AND B.item.status = :status")
    List<Item> findUserActiveBids(@Param("userId") Long userId, @Param("status") Item.ItemStatus status);

    @Query("SELECT DISTINCT B.item FROM Bid B WHERE B.user.id = :userId AND B.item.endDate < CURRENT_TIMESTAMP")
    List<Item> findUserBidHistoryItems(@Param("userId") Long userId);

    @Query("SELECT count(B) FROM Bid B WHERE B.item.id IN(:itemIds) GROUP BY B.item.id")
    List<Object[]> countByItemId(@Param("itemIds") List<Long> itemIds);

    @Query("SELECT MAX(b.bidAmount) FROM Bid b WHERE b.item.id = :itemId")
    BigDecimal findMaxBidAmountByitemId(@Param("itemId") Long itemId);

    @Query("SELECT b FROM Bid b WHERE b.item.id IN :itemIds AND b.isCurrentHighestBid = true")
    List<Bid> findHighestBidsByItemIds(@Param("itemIds") List<Long> itemIds);

    @Modifying
    @Transactional
    @Query("UPDATE Bid b SET b.isCurrentHighestBid = false WHERE b.item.id = :itemId AND b.isCurrentHighestBid = true")
    int resetCurrentHighestBid(@Param("itemId") Long itemId);

    @Query("SELECT DISTINCT b.user FROM Bid b WHERE b.item.id = :itemId")
    List<User> findAllUserByItemid(@Param("itemId") Long itemId);
}
