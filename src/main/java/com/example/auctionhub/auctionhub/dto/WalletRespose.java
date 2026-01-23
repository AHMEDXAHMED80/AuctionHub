package com.example.auctionhub.auctionhub.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.example.auctionhub.auctionhub.models.Wallet.walletType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class WalletRespose {
    private BigDecimal availableBalance;
    private BigDecimal totalSpend;
    private walletType walletType;
    private BigDecimal frozenBalance;
    private LocalDateTime updatedAt;
}
