package com.example.auctionhub.auctionhub.repository;

import com.example.auctionhub.auctionhub.models.AuctionWinner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for AuctionWinner entity persistence and queries.
 */
@Repository
public interface AuctionWinnerRepository extends JpaRepository<AuctionWinner, Long> {
    // Add custom query methods if needed
}
