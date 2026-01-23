package com.example.auctionhub.auctionhub.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.auctionhub.auctionhub.dto.BidResponse;
import com.example.auctionhub.auctionhub.models.Bid;

/**
 * MapStruct mapper for converting Bid entities to BidResponse DTOs.
 */
@Mapper(componentModel = "spring")
public interface BidMapper {

    /**
     * Maps a Bid entity to a BidResponse DTO.
     *
     * @param bid Bid entity to map
     * @return BidResponse DTO
     */
    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "item.title", target = "itemName")
    BidResponse toBidResponse(Bid bid);
}
