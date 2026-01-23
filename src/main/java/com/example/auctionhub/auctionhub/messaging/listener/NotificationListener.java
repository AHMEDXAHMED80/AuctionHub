package com.example.auctionhub.auctionhub.messaging.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.example.auctionhub.auctionhub.messaging.publisher.NotificationPublisher.NotificationMessage;
import com.example.auctionhub.auctionhub.models.Notifications;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.repository.NotificationRepository;
import com.example.auctionhub.auctionhub.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {
    
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @RabbitListener(queues = "notification.queue")
    public void handleNotification(NotificationMessage message){
        log.info("Received notification from RabbitMQ: {}", message);

        try {
            
            User user = userRepository.findById(message.userId()).orElseThrow(
                () -> new RuntimeException("User not found with id: {} " + message.userId())
            );

            Notifications notification = new Notifications();

            notification.setUser(user);
            notification.setType(message.type());
            notification.setRead(false);
            
            notificationRepository.save(notification);
            log.info("notfication saved in the database for user", user.getId());
        } catch (Exception e) {
            log.error("Failed to process notification: {}", e.getMessage(), e);
            throw e;
        }

    }
    
}
