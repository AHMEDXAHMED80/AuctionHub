package com.example.auctionhub.auctionhub.mapper;

import java.time.LocalDateTime;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.auctionhub.auctionhub.dto.UserBidHistoryResponse;
import com.example.auctionhub.auctionhub.models.Bid;

@Mapper(componentModel = "spring")
public interface UserBidHistoryMapper {
    @Mapping(source = "defaultImageUrl", target = "defualtimageUrl")
    UserBidHistoryResponse toUserBidHistoryResponse(Long itemId, String title, String defaultImageUrl, String lastBidAmount, Bid.bidStatus status, LocalDateTime endedAt);
}
