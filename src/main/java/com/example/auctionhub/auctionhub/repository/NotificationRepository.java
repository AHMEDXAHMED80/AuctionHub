package com.example.auctionhub.auctionhub.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.example.auctionhub.auctionhub.models.Notifications;

@Repository
public interface NotificationRepository extends JpaRepository<Notifications, Long> {
    List<Notifications> findByUserIdOrderByCreatedAtDesc(Long userId);
    Page<Notifications> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<Notifications> findByUserIdAndIsReadFalse(Long userId);
    Long countByUserIdAndIsReadFalse(Long userId);

    @Transactional
    @Modifying
    @Query("UPDATE Notifications n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId);

}
