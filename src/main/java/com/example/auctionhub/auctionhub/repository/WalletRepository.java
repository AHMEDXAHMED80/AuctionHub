package com.example.auctionhub.auctionhub.repository;

import com.example.auctionhub.auctionhub.models.Wallet;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    // Add custom query methods if needed

    Optional<Wallet> findByUserId(Long userId);
}
