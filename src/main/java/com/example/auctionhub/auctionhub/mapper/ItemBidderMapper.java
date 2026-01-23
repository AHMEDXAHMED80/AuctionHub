package com.example.auctionhub.auctionhub.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.auctionhub.auctionhub.dto.ItemBidderResponse;
import com.example.auctionhub.auctionhub.models.Item;

/**
 * MapStruct mapper for converting Item entities to ItemBidderResponse DTOs for bidders.
 */
@Mapper(componentModel = "spring")
public interface ItemBidderMapper {

    /**
     * Maps an Item entity to an ItemBidderResponse DTO for bidders.
     *
     * @param item Item entity to map
     * @return ItemBidderResponse DTO
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "startingPrice", source = "startingPrice")
    @Mapping(target = "currentHighestBid", source = "currentHighestBid")
    @Mapping(target = "startDate", source = "startDate")
    @Mapping(target = "endDate", source = "endDate")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "sellerUsername", source = "seller.username")
    @Mapping(target = "sellerId", source = "seller.id")
    @Mapping(target = "totalBids", ignore = true)
    ItemBidderResponse toItemBidderResponse(Item item);
}
