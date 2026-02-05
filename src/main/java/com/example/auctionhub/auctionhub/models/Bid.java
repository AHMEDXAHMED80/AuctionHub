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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity representing a bid placed by a user on an item in the auction system.
 * Includes bid amount, status, and relationships to user, item, and wallet.
 */
@Entity
@Table(name = "bids")
@Getter
@Setter
public class Bid {
    /**
     * Enum for bid status (ACTIVE, WON, LOST, CANCELLED).
     */
    public enum bidStatus{
        ACTIVE,
        WON, 
        LOST,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "id")
    private User user;
    
    @ManyToOne
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;
    
    private BigDecimal bidAmount = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    private bidStatus status = bidStatus.ACTIVE;
    
    private boolean isCurrentHighestBid;
    
    /**
     * Winner record if this bid won the auction (null if bid didn't win)
     */
    @OneToOne(mappedBy = "winningBid")
    private AuctionWinner auctionWinner;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @Version
    private long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
