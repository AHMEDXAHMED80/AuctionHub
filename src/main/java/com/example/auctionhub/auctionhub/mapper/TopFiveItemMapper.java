package com.example.auctionhub.auctionhub.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.auctionhub.auctionhub.dto.TopFiveItemResponse;
import com.example.auctionhub.auctionhub.models.Item;

@Mapper(componentModel = "spring")
public interface TopFiveItemMapper {
    @Mapping(target = "firstIndexUrlImage", ignore = true)
    @Mapping(source = "currentHighestBid", target = "currentPrice")
    TopFiveItemResponse toTopFiveItemResponse(Item item);
}
