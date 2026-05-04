package com.example.auctionhub.auctionhub.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import com.example.auctionhub.auctionhub.dto.NotificationResponseDto;
import com.example.auctionhub.auctionhub.models.Notifications;

/**
 * MapStruct mapper for converting Notifications entities to response DTOs.
 */
@Mapper(componentModel = "spring")
public interface NotificationMapper {

    /**
     * Maps a Notifications entity to NotificationResponseDto.
     *
     * @param notification Notifications entity to map
     * @return NotificationResponseDto
     */
    NotificationResponseDto toNotificationResponseDto(Notifications notification);
    /**
     * Maps a list of Notifications entities to NotificationResponseDto list.
     *
     * @param notifications Notifications entities
     * @return List of NotificationResponseDto
     */
    List<NotificationResponseDto> toNotificationResponseDtos(List<Notifications> notifications);

    /**
     * Maps enum type to String in DTO.
     */
    default String map(Notifications.NotificationType type) {
        return type != null ? type.name() : null;
    }
}
