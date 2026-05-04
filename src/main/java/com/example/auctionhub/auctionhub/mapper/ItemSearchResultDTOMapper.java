package com.example.auctionhub.auctionhub.mapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.example.auctionhub.auctionhub.dto.ItemSearchResultDTO;
import com.example.auctionhub.auctionhub.models.Item;

@Mapper(componentModel = "spring")
public interface ItemSearchResultDTOMapper {
    @Mapping(target = "imagesUrlList", ignore = true)
    @Mapping(source = "currentHighestBid", target = "currentPrice")
    @Mapping(target = "timeRemaining", ignore = true)
    ItemSearchResultDTO toItemSearchResponse(Item item);
} 