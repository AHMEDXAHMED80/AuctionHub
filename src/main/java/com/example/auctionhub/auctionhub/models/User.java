package com.example.auctionhub.auctionhub.models;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity representing a user in the auction system.
 * Includes authentication, profile, and role/status information.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    /**
     * Enum for user roles (USER, POSTER, ADMIN).
     */
    public enum roles{
        USER,
        POSTER, 
        ADMIN
    }

    /**
     * Enum for user status (blocked, active).
     */
    public enum status{
        blocked,
        active
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "First name must not be blank")
    @Column(name = "fname")
    private String first_name;
    @NotBlank(message = "Last name must not be blank")
    @Column(name = "lname")
    private String last_name;
    @NotBlank(message = "Email must not be blank")
    @Column(unique = true)
    private String email;
    @NotBlank(message = "Username must not be blank")
    @Column(unique = true)
    private String username;
    @NotBlank(message = "Password must not be blank")
    private String password;
    @NotNull(message = "Birthdate must not be null")
    @Column(name = "birthdate")
    private LocalDate birthDate;
    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private roles role;
    @NotNull
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @NotNull
    @Column(name = "updated_at", updatable = true)
    private LocalDateTime updatedAt;
    @NotNull
    @Enumerated(EnumType.STRING)
    private status userStatus;
    
    @OneToOne(mappedBy = "user")
    private Wallet wallet;
    
    @OneToMany(mappedBy = "user")
    private List<Bid> bids;
    
    @OneToMany(mappedBy = "user")
    private List<Payment> payments;
    
    @OneToMany(mappedBy = "seller")
    private List<Item> sellerItems;

    @OneToMany(mappedBy = "user")
    private List<Notifications> notifications;
    
    /**
     * Auctions won by this user
     */
    @OneToMany(mappedBy = "winner")
    private List<AuctionWinner> auctionsWon;
    
    @Version
    private long version;

    @PrePersist
    protected void onCreate(){
        createdAt=updatedAt=LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate(){
        updatedAt= LocalDateTime.now();
    }
}
