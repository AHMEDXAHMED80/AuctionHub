package com.example.auctionhub.auctionhub.messaging.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.example.auctionhub.auctionhub.dto.NotificationResponseDto;
import com.example.auctionhub.auctionhub.mapper.NotificationMapper;
import com.example.auctionhub.auctionhub.messaging.publisher.NotificationPublisher.NotificationMessage;
import com.example.auctionhub.auctionhub.models.Notifications;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.repository.NotificationRepository;
import com.example.auctionhub.auctionhub.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NotificationListener {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationListener(NotificationRepository notificationRepository,
                                UserRepository userRepository,
                                NotificationMapper notificationMapper,
                                SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationMapper = notificationMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @RabbitListener(queues = "notification.queue")
    public void handleNotification(NotificationMessage message){
        if (message == null || message.userId() == null || message.type() == null) {
            log.warn("Skipping invalid notification message: {}", message);
            return;
        }

        User user = userRepository.findById(message.userId()).orElse(null);
        if (user == null) {
            log.warn("Skipping notification; user not found with id {}", message.userId());
            return;
        }

        Notifications notification = new Notifications();
        notification.setUser(user);
        notification.setType(message.type());
        String content = (message.message() == null || message.message().isBlank())
                ? message.type().name()
                : message.message();
        notification.setMessage(content);
        notification.setRead(false);

        Notifications saved = notificationRepository.save(notification);
        try {
            NotificationResponseDto dto = notificationMapper.toNotificationResponseDto(saved);
            messagingTemplate.convertAndSendToUser(user.getUsername(), "/queue/notifications", dto);
            log.info("Saved and delivered notification {} for username {}", saved.getId(), user.getUsername());
        } catch (Exception ex) {
            // Keep DB write as source of truth even if realtime delivery temporarily fails.
            log.error("Saved notification {} but failed realtime delivery for username {}: {}",
                    saved.getId(), user.getUsername(), ex.getMessage(), ex);
        }
    }
    
}
