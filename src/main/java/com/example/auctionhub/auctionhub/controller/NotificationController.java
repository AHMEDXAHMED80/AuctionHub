package com.example.auctionhub.auctionhub.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.auctionhub.auctionhub.dto.ListResponse;
import com.example.auctionhub.auctionhub.dto.NotificationResponseDto;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.security.SecurityUtils;
import com.example.auctionhub.auctionhub.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for user notifications.
 * Exposes APIs for listing notifications and marking all as read.
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Returns all notifications for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<ListResponse<NotificationResponseDto>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User currentUser = SecurityUtils.getAuthenticatedUserOrThrow();
        log.info("Fetching notifications for user {}", currentUser.getId());

        Page<NotificationResponseDto> notifications = notificationService.getNotifications(
                currentUser.getId(), PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(new ListResponse<>(notifications));
    }

    /**
     * Marks all notifications as read for the authenticated user.
     */
    @PatchMapping("/read-all")
    public ResponseEntity<MessageResponse> markAllAsRead() {
        User currentUser = SecurityUtils.getAuthenticatedUserOrThrow();
        log.info("Marking all notifications as read for user {}", currentUser.getId());

        notificationService.markAllAsRead(currentUser.getId());
        return ResponseEntity.ok(new MessageResponse("All notifications marked as read"));
    }

    public record MessageResponse(String message) {}
}
