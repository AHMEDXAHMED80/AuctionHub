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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

/**
 * Entity representing an auction item in the system.
 * Includes item details, status, category, and image relationships.
 */
@Entity
@Table(name = "items")
@Getter
@Setter
public class Item {

    /**
     * Enum for item categories (ELECTRONICS, FURNITURE, etc.).
     */
    public enum ItemCategory {
        ELECTRONICS,
        FURNITURE,
        CLOTHING,
        BOOKS,
        ART,
        VEHICLES,
        REAL_ESTATE,
        JEWELRY,
        COLLECTIBLES,
        SPORTS,
        HOME_AND_GARDEN,
        TOYS,
        OTHER
    }

    /**
     * Enum for item status (ACTIVE, ENDED, SOLD, CANCELLED, PENDING).
     */
    public enum ItemStatus {
        ACTIVE,
        EXPIRED,
        SOLD,
        CANCELLED,
        PENDING
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String title;
    
    private String description;
    @OneToMany(mappedBy = "item", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private List<ItemImages> itemImages;
    
    @Enumerated(EnumType.STRING)
    private ItemCategory category;
    
    private BigDecimal startingPrice = BigDecimal.ZERO;
    
    private BigDecimal currentHighestBid = BigDecimal.ZERO;
    
    @ManyToOne
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;
    
    @OneToMany(mappedBy = "item")
    private List<Bid> bids;
    
    /**
     * Winner records for this item (could be multiple if relisted)
     */
    @OneToMany(mappedBy = "item")
    private List<AuctionWinner> winners;
    
    private LocalDateTime startDate;
    
    private LocalDateTime endDate;
    
    @Enumerated(EnumType.STRING)
    private ItemStatus status = ItemStatus.PENDING;
    
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
