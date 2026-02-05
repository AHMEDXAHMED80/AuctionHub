package com.example.auctionhub.auctionhub.service;

import java.math.BigDecimal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public WalletRespose createWallet() {
        try {
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
        } catch (Exception e) {
            log.error("Error creating wallet: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create wallet", e);
        }
    }

    /**
     * Adds balance to the current authenticated user's wallet.
     *
     * @param amount Amount to add (must be positive)
     * @return Updated wallet response DTO
     * @throws IllegalArgumentException if amount is null or not positive
     * @throws RuntimeException if wallet not found
     */
    @Transactional
    public WalletRespose addBalance(BigDecimal amount) {
        try {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }

            User currentUser = SecurityUtils.getAuthenticatedUserOrThrow();
            log.info("Adding balance for user {} with amount: ${}", currentUser.getId(), amount);

            Wallet wallet = walletRepository.findByUserId(currentUser.getId())
                    .orElseThrow(() -> {
                        log.error("Wallet not found for user: {}", currentUser.getId());
                        return new RuntimeException("Wallet not found. Please create a wallet first.");
                    });

            BigDecimal oldBalance = wallet.getAvailableBalance();
            BigDecimal newBalance = oldBalance.add(amount);

            wallet.setAvailableBalance(newBalance);
            walletRepository.save(wallet);

            log.info("Balance added successfully for user {} - Previous: ${}, Added: ${}, New: ${}",
                    currentUser.getId(), oldBalance, amount, newBalance);

            return walletMapper.toWalletResponse(wallet);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error adding balance: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add balance", e);
        }
    }

    /**
     * Credits wallet balance for a specific user
     *
     * Used by StripeService when a payment succeeds via webhook.
     * This is the ONLY method that should add funds to a wallet after payment.
     *
     * @param userId User ID whose wallet to credit
     * @param amount Amount to credit (must be positive)
     * @throws RuntimeException if wallet not found
     */
    @Transactional
    public void creditBalance(Long userId, java.math.BigDecimal amount) {
        try {
            log.info("Crediting wallet for user {} with amount: ${}", userId, amount);

            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(() -> {
                        log.error("Wallet not found for user: {}", userId);
                        return new RuntimeException("Wallet not found for user: " + userId);
                    });

            BigDecimal oldBalance = wallet.getAvailableBalance();
            BigDecimal newBalance = oldBalance.add(amount);

            wallet.setAvailableBalance(newBalance);
            walletRepository.save(wallet);

            log.info("Wallet credited successfully for user {} - Previous: ${}, Added: ${}, New: ${}",
                    userId, oldBalance, amount, newBalance);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error crediting wallet for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to credit wallet for user " + userId, e);
        }
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
    @Transactional
    public void deductBalance(Long userId, java.math.BigDecimal amount) {
        try {
            log.info("Deducting from wallet for user {} amount: ${}", userId, amount);

            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(() -> {
                        log.error("Wallet not found for user: {}", userId);
                        return new RuntimeException("Wallet not found for user: " + userId);
                    });

            BigDecimal oldBalance = wallet.getAvailableBalance();

            if (oldBalance.compareTo(amount) < 0) {
                log.warn("Insufficient balance for user {} - Balance: ${}, Attempted deduction: ${}",
                        userId, oldBalance, amount);
                throw new RuntimeException("Insufficient balance for refund");
            }

            BigDecimal newBalance = oldBalance.subtract(amount);
            wallet.setAvailableBalance(newBalance);
            walletRepository.save(wallet);

            log.info("Wallet debited successfully for user {} - Previous: ${}, Deducted: ${}, New: ${}",
                    userId, oldBalance, amount, newBalance);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deducting from wallet for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to deduct from wallet for user " + userId, e);
        }
    }

    /**
     * Adds amount to frozen balance for a specific user.
     * Moves funds from available balance to frozen balance.
     *
     * Used when a bid is placed to freeze the bid amount.
     *
     * @param userId User ID whose wallet to update
     * @param amount Amount to freeze (must be positive)
     * @throws RuntimeException if wallet not found or insufficient available balance
     */
    @Transactional
    public void addToFrozenBalance(Long userId, BigDecimal amount) {
        try {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }

            log.info("Freezing {} for user {}", amount, userId);

            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(() -> {
                        log.error("Wallet not found for user: {}", userId);
                        return new RuntimeException("Wallet not found for user: " + userId);
                    });

            BigDecimal availableBalance = wallet.getAvailableBalance();

            if (availableBalance.compareTo(amount) < 0) {
                log.warn("Insufficient available balance for user {} - Available: ${}, Attempted freeze: ${}",
                        userId, availableBalance, amount);
                throw new RuntimeException("Insufficient available balance to freeze");
            }

            wallet.setAvailableBalance(availableBalance.subtract(amount));
            wallet.setFrozenBalance(wallet.getFrozenBalance().add(amount));
            walletRepository.save(wallet);

            log.info("Frozen balance updated for user {} - Available: ${}, Frozen: ${}",
                    userId, wallet.getAvailableBalance(), wallet.getFrozenBalance());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error freezing balance for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to freeze balance for user " + userId, e);
        }
    }

    /**
     * Deducts amount from frozen balance for a specific user.
     * Returns funds from frozen balance to available balance.
     *
     * Used when a bid is outbid to unfreeze the previous bid amount.
     *
     * @param userId User ID whose wallet to update
     * @param amount Amount to unfreeze (must be positive)
     * @throws RuntimeException if wallet not found or insufficient frozen balance
     */
    @Transactional
    public void deductFromFrozenBalance(Long userId, BigDecimal amount) {
        try {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }

            log.info("Unfreezing {} for user {}", amount, userId);

            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(() -> {
                        log.error("Wallet not found for user: {}", userId);
                        return new RuntimeException("Wallet not found for user: " + userId);
                    });

            BigDecimal frozenBalance = wallet.getFrozenBalance();

            if (frozenBalance.compareTo(amount) < 0) {
                log.warn("Insufficient frozen balance for user {} - Frozen: ${}, Attempted unfreeze: ${}",
                        userId, frozenBalance, amount);
                throw new RuntimeException("Insufficient frozen balance to unfreeze");
            }

            wallet.setFrozenBalance(frozenBalance.subtract(amount));
            wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount));
            walletRepository.save(wallet);

            log.info("Unfrozen balance updated for user {} - Available: ${}, Frozen: ${}",
                    userId, wallet.getAvailableBalance(), wallet.getFrozenBalance());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error unfreezing balance for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to unfreeze balance for user " + userId, e);
        }
    }

    /**
     * Transfers amount from one user's frozen balance to another user's available balance.
     * Used when an auction ends to transfer funds from winner to seller.
     *
     * @param fromUserId User ID whose frozen balance to deduct from
     * @param toUserId User ID whose available balance to credit
     * @param amount Amount to transfer (must be positive)
     * @throws RuntimeException if wallet not found or insufficient frozen balance
     */
    @Transactional
    public Boolean transferFrozenToSellerBalance(Long fromUserId, Long toUserId, BigDecimal amount) {
        try {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }

            log.info("Transferring {} from user {} frozen balance to user {} available balance",
                    amount, fromUserId, toUserId);

            Wallet fromWallet = walletRepository.findByUserId(fromUserId)
                    .orElseThrow(() -> {
                        log.error("Wallet not found for user: {}", fromUserId);
                        return new RuntimeException("Wallet not found for user: " + fromUserId);
                    });

            Wallet toWallet = walletRepository.findByUserId(toUserId)
                    .orElseThrow(() -> {
                        log.error("Wallet not found for user: {}", toUserId);
                        return new RuntimeException("Wallet not found for user: " + toUserId);
                    });

            BigDecimal frozenBalance = fromWallet.getFrozenBalance();

            if (frozenBalance.compareTo(amount) < 0) {
                log.warn("Insufficient frozen balance for user {} - Frozen: ${}, Attempted transfer: ${}",
                        fromUserId, frozenBalance, amount);
                throw new RuntimeException("Insufficient frozen balance to transfer");
            }

            fromWallet.setFrozenBalance(frozenBalance.subtract(amount));
            toWallet.setAvailableBalance(toWallet.getAvailableBalance().add(amount));
            walletRepository.save(fromWallet);
            walletRepository.save(toWallet);

            log.info("Transfer completed - From user {} frozen: ${}, To user {} available: ${}",
                    fromUserId, fromWallet.getFrozenBalance(), toUserId, toWallet.getAvailableBalance());
            return true;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error transferring from user {} to user {}: {}", fromUserId, toUserId, e.getMessage(), e);
            throw new RuntimeException("Failed to transfer funds from user " + fromUserId + " to user " + toUserId, e);
        }
    }
}