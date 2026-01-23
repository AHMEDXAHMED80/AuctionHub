package com.example.auctionhub.auctionhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.example.auctionhub.auctionhub.models.User;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByUsername(String username);
    
    boolean existsByEmail(String email);
    
    boolean existsByUsername(String username);

    @Query(
        "select u from User u " +
        "WHERE u.username =?1 OR u.email = ?2"
    )

    User findByUsernameOrEmail(String username, String email);

}