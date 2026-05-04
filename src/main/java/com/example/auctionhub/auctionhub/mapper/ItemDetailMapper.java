package com.example.auctionhub.auctionhub.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.auctionhub.auctionhub.dto.ItemDetailDTO;
import com.example.auctionhub.auctionhub.models.Item;

@Mapper(componentModel = "spring")
public interface ItemDetailMapper {

    @Mapping(source = "seller.id",       target = "sellerId")
    @Mapping(source = "seller.username", target = "sellerName")
    @Mapping(target = "sellerRating",    ignore = true)
    @Mapping(target = "timeRemaining",   ignore = true)
    @Mapping(target = "imageUrls",       ignore = true)
    @Mapping(target = "bidCount",        ignore = true)
    @Mapping(target = "recentBids",      ignore = true)
    @Mapping(target = "hasUserBid",      ignore = true)
    @Mapping(target = "isUserWatching",  ignore = true)
    @Mapping(target = "isUserSeller",    ignore = true)
    ItemDetailDTO toDto(Item item);
}
