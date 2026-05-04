package com.example.auctionhub.auctionhub.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.dto.AuthRegisterRequest;
import com.example.auctionhub.auctionhub.dto.UserResponse;

/**
 * MapStruct mapper for converting User entities to UserResponse DTOs and mapping AuthRegisterRequest to User.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Maps a User entity to a UserResponse DTO.
     *
     * @param user User entity to map
     * @return UserResponse DTO
     */
    @Mapping(source = "first_name", target = "firstName")
    @Mapping(source = "last_name", target = "lastName")
    @Mapping(source = "userStatus", target = "status")
    UserResponse userToUserResponse(User user);

    /**
     * Maps an AuthRegisterRequest DTO to a User entity, ignoring sensitive and system fields.
     *
     * @param request AuthRegisterRequest DTO
     * @return User entity
     */
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "userStatus", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "wallet", ignore = true)
    @Mapping(target = "bids", ignore = true)
    @Mapping(target = "payments", ignore = true)
    @Mapping(target = "sellerItems", ignore = true)
    @Mapping(target = "notifications", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "auctionsWon", ignore = true)
    @Mapping(source = "firstName", target = "first_name")
    @Mapping(source = "lastName", target = "last_name")
    User authRegisterRequestToUser(AuthRegisterRequest request);
}
