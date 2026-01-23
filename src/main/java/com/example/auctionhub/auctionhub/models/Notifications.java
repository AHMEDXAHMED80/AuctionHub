package com.example.auctionhub.auctionhub.models;
import java.time.LocalDateTime;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity representing a notification for a user in the auction system.
 * Used for bid, item, and payment events.
 */
@Entity
@Table (name = "notifications")
@Getter
@Setter
public class Notifications {

    /**
     * Enum for notification types (bid placed, outbid, item won/lost, payment received).
     */
    public enum NotificationType {
        BID_PLACED,
        BID_OUTBID,
        ITEM_WON,
        ITEM_LOST,
        PAYMENT_RECEIVED,
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;

    private boolean isRead;
    
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
