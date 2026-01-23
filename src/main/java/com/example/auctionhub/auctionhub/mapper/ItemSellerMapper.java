package com.example.auctionhub.auctionhub.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.example.auctionhub.auctionhub.dto.ItemResponse;
import com.example.auctionhub.auctionhub.dto.ItemSellerRequest;
import com.example.auctionhub.auctionhub.models.Item;

/**
 * MapStruct mapper for converting Item entities to ItemResponse DTOs and mapping ItemSellerRequest to Item.
 */
@Mapper(componentModel = "spring")
public interface ItemSellerMapper {

    /**
     * Maps an Item entity to an ItemResponse DTO for sellers.
     *
     * @param item Item entity to map
     * @return ItemResponse DTO
     */
    @Mapping(target = "imagesUrlList", ignore = true)
    @Mapping(source = "currentHighestBid", target = "currentPrice")
    @Mapping(source = "seller.username", target = "sellerUsername")
    @Mapping(target = "isActive", expression = "java(item.getStatus() == Item.ItemStatus.ACTIVE)")
    ItemResponse toItemResponse(Item item);

    /**
     * Maps an ItemSellerRequest DTO to an Item entity, ignoring system fields.
     *
     * @param itemSellerRequest ItemSellerRequest DTO
     * @return Item entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "itemImages", ignore = true)
    @Mapping(target = "currentHighestBid", ignore = true)
    @Mapping(target = "seller", ignore = true)
    @Mapping(target = "bids", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Item mapToItem(ItemSellerRequest itemSellerRequest);
}
