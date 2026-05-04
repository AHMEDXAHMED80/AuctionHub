package com.example.auctionhub.auctionhub.models;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity representing a user's wallet in the auction system.
 * Tracks balances, wallet type, and user relationship.
 */
@Entity
@Table(name = "wallet")
@Getter
@Setter
public class Wallet {
    /**
     * Enum for wallet type (BIDDER, SELLER).
     */
    public enum walletType{
        BIDDER,
        SELLER
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "id")
    private User user;
    @NotNull
    private BigDecimal availableBalance=BigDecimal.ZERO;
    @NotNull
    private BigDecimal totalSpend=BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @NotNull
    private walletType walletType;
    @NotNull
    private BigDecimal frozenBalance = BigDecimal.ZERO;
    private LocalDateTime updatedAt;

    @Version
    private long version;

    @PreUpdate
    protected void onUpdate() { 
        updatedAt = LocalDateTime.now();
    }

}