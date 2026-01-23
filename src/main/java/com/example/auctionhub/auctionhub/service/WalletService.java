package com.example.auctionhub.auctionhub.service;

import java.math.BigDecimal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.example.auctionhub.auctionhub.dto.WalletRespose;
import com.example.auctionhub.auctionhub.mapper.WalletMapper;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.models.Wallet;
import com.example.auctionhub.auctionhub.models.Wallet.walletType;
import com.example.auctionhub.auctionhub.repository.WalletRepository;
import com.example.auctionhub.auctionhub.security.SecurityUtils;


/**
 * Service for managing user wallets, including creation and retrieval.
 * Handles wallet type assignment based on user role and delegates mapping to WalletMapper.
 */
@Slf4j
@Service
public class WalletService {

    /**
     * Repository for wallet persistence operations.
     */
    private final WalletRepository walletRepository;

    /**
     * Mapper for converting Wallet entities to response DTOs.
     */
    private final WalletMapper walletMapper;

    /**
     * Constructs a WalletService with required dependencies.
     *
     * @param walletRepository Wallet repository for persistence
     * @param walletMapper     Mapper for Wallet to WalletRespose
     */
    public WalletService(WalletRepository walletRepository, WalletMapper walletMapper) {
        this.walletRepository = walletRepository;
        this.walletMapper = walletMapper;
    }

    /**
     * Retrieves the current user's wallet.
     *
     * @return Wallet response DTO with balance and wallet details, or null if not found
     */
    public WalletRespose getWallet() {
        User currentUser = SecurityUtils.getCurrentUser();
        Wallet wallet = walletRepository.findByUserId(currentUser.getId()).orElse(null);
        return walletMapper.toWalletResponse(wallet);
    }

    /**
     * Creates a new wallet for the current user.
     * Wallet type is automatically assigned based on user role:
     * - USER role → BIDDER wallet
     * - Other roles → SELLER wallet
     *
     * @return Created wallet response DTO
     */
    public WalletRespose createWallet() {
        User currentUser = SecurityUtils.getCurrentUser();
        log.info("Creating wallet for user {} with role {}", currentUser.getId(), currentUser.getRole());
        Wallet newWallet = new Wallet();
        newWallet.setUser(currentUser);
        if (currentUser.getRole() == User.roles.USER) {
            newWallet.setWalletType(walletType.BIDDER);
        } else {
            newWallet.setWalletType(walletType.SELLER);
        }

        walletRepository.save(newWallet);
        log.info("Wallet created successfully for user {} with type {}", currentUser.getId(),
                newWallet.getWalletType());

        return walletMapper.toWalletResponse(newWallet);
    }

    /**
     * Credits wallet balance for a specific user
     * 
     * Used by StripeService when a payment succeeds via webhook.
     * This is the ONLY method that should add funds to a wallet after payment.
     * 
     * Thread-safe: Multiple concurrent credits to the same wallet may cause race
     * conditions.
     * Consider adding @Transactional with pessimistic locking for high-volume
     * systems.
     * 
     * @param userId User ID whose wallet to credit
     * @param amount Amount to credit (must be positive)
     * @throws RuntimeException if wallet not found
     */
    public void creditBalance(Long userId, java.math.BigDecimal amount) {
        log.info("Crediting wallet for user {} with amount: ${}", userId, amount);

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.error("Wallet not found for user: {}", userId);
                    return new RuntimeException("Wallet not found for user: " + userId);
                });

        BigDecimal oldBalance = wallet.getAvailableBalance();
        BigDecimal newBalance = oldBalance.add(amount);

        // Add amount to current balance
        wallet.setAvailableBalance(newBalance);
        walletRepository.save(wallet);

        log.info("Wallet credited successfully for user {} - Previous: ${}, Added: ${}, New: ${}",
                userId, oldBalance, amount, newBalance);
    }

    /**
     * Deducts balance from wallet
     * 
     * Used for refunds when a successful payment is reversed.
     * Validates sufficient balance before deduction to prevent negative balances.
     * 
     * @param userId User ID whose wallet to debit
     * @param amount Amount to deduct (must be positive)
     * @throws RuntimeException if wallet not found or insufficient balance
     */
    public void deductBalance(Long userId, java.math.BigDecimal amount) {
        log.info("Deducting from wallet for user {} amount: ${}", userId, amount);

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.error("Wallet not found for user: {}", userId);
                    return new RuntimeException("Wallet not found for user: " + userId);
                });

        BigDecimal oldBalance = wallet.getAvailableBalance();

        // Prevent negative balance
        if (oldBalance.compareTo(amount) < 0) {
            log.warn("Insufficient balance for user {} - Balance: ${}, Attempted deduction: ${}",
                    userId, oldBalance, amount);
            throw new RuntimeException("Insufficient balance for refund");
        }

        BigDecimal newBalance = oldBalance.subtract(amount);

        // Subtract amount from current balance
        wallet.setAvailableBalance(newBalance);
        walletRepository.save(wallet);

        log.info("Wallet debited successfully for user {} - Previous: ${}, Deducted: ${}, New: ${}",
                userId, oldBalance, amount, newBalance);
    }
}