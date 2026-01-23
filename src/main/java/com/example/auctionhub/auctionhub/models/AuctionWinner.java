package com.example.auctionhub.auctionhub.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents the winner of an auction after it has ended.
 * Tracks fund transfer from winner's wallet to seller's wallet.
 * Note: Payment already completed via Stripe when user topped up wallet.
 */
@Entity
@Table(name = "auction_winners")
@Getter
@Setter
public class AuctionWinner {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * The user who won the auction (buyer/winner)
     */
    @ManyToOne
    @JoinColumn(name = "winner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_auction_winner_user"))
    private User winner;
    
    /**
     * The item that was won in the auction
     */
    @ManyToOne
    @JoinColumn(name = "item_id", nullable = false, foreignKey = @ForeignKey(name = "fk_auction_winner_item"))
    private Item item;
    
    /**
     * The winning bid that secured the auction
     */
    @ManyToOne
    @JoinColumn(name = "bid_id", nullable = false, foreignKey = @ForeignKey(name = "fk_auction_winner_bid"))
    private Bid winningBid;
    
    /**
     * Final winning bid amount (denormalized for quick access)
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal finalBidAmount;
    
    /**
     * Timestamp when the auction ended and winner was determined
     */
    @Column(nullable = false)
    private LocalDateTime wonAt;
    
    /**
     * When frozen funds were transferred from winner to seller
     */
    @Column(nullable = false)
    private LocalDateTime fundsTransferredAt;
    
    /**
     * Status of the post-auction process
     * Note: No payment step needed - funds already in wallet from Stripe
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WinnerStatus status = WinnerStatus.FUNDS_TRANSFERRED;
    
    /**
     * Post-auction winner status tracking
     * Payment already done via wallet - only tracking fund transfer and disputes
     */
    public enum WinnerStatus {
        FUNDS_TRANSFERRED,  // Frozen funds transferred to seller (default)
        COMPLETED,          // Transaction completed successfully
        DISPUTED,           // Issue raised by buyer/seller
        REFUNDED,           // Funds returned to winner (dispute resolved)
        TRANSFER_FAILED     // Fund transfer failed (insufficient frozen balance)
    }
}
