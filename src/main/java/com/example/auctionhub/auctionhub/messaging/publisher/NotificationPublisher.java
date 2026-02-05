package com.example.auctionhub.auctionhub.messaging.publisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import com.example.auctionhub.auctionhub.models.Notifications;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;

    public NotificationPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    private static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    private static final String NOTIFICATION_ROUTING_KEY = "notification.send";

    public void publishNotification(Long userId, String message, Notifications.NotificationType type) {
        NotificationMessage notificationMessage = new NotificationMessage(userId, message, type);
        log.info("Publishing notification to RabbitMQ: {}", notificationMessage);
        rabbitTemplate.convertAndSend(NOTIFICATION_EXCHANGE, NOTIFICATION_ROUTING_KEY, notificationMessage);
        log.info("Notification published successfully for user {}", userId);
    }

    public record NotificationMessage(Long userId, String message, Notifications.NotificationType type ) {}
}
