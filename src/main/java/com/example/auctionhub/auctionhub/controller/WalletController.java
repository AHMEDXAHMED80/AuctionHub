package com.example.auctionhub.auctionhub.controller;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auctionhub.auctionhub.dto.WalletRespose;
import com.example.auctionhub.auctionhub.mapper.BalanceResponseMapper;
import com.example.auctionhub.auctionhub.service.WalletService;

import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for wallet operations.
 * Handles wallet creation, balance queries, and deposits.
 */
@Slf4j
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;
    private final BalanceResponseMapper balanceResponseMapper;

    public WalletController(WalletService walletService, BalanceResponseMapper balanceResponseMapper) {
        this.walletService = walletService;
        this.balanceResponseMapper = balanceResponseMapper;
    }

    /**
     * Get current user's wallet information
     * GET /api/wallet
     */
    @GetMapping
    public ResponseEntity<WalletRespose> getWallet() {
        log.info("Fetching wallet for current user");
        WalletRespose wallet = walletService.getWallet();
        return ResponseEntity.ok(wallet);
    }

    /**
     * Create a new wallet for the current user
     * POST /api/wallet
     */
    @PostMapping
    public ResponseEntity<WalletRespose> createWallet() {
        log.info("Creating wallet for current user");
        WalletRespose wallet = walletService.createWallet();
        return ResponseEntity.status(HttpStatus.CREATED).body(wallet);
    }

    /**
     * Add balance to current user's wallet
     * POST /api/wallet/deposit
     */
    @PostMapping("/deposit")
    public ResponseEntity<WalletRespose> addBalance(@RequestBody AddBalanceRequest request) {
        log.info("Adding balance {} to wallet", request.amount());

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().build();
        }

        WalletRespose wallet = walletService.addBalance(request.amount());
        return ResponseEntity.ok(wallet);
    }

    /**
     * Get wallet balance summary
     * GET /api/wallet/balance
     */
    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance() {
        log.info("Fetching balance for current user");
        WalletRespose wallet = walletService.getWallet();
        BalanceResponse balanceResponse = balanceResponseMapper.toBalanceResponse(wallet);
        return ResponseEntity.ok(balanceResponse);
    }

    // Request/Response DTOs
    public record AddBalanceRequest(BigDecimal amount) {}

    public record BalanceResponse(
        BigDecimal availableBalance,
        BigDecimal frozenBalance,
        BigDecimal totalBalance
    ) {}
}
