package com.example.auctionhub.auctionhub.mapper;

import org.mapstruct.Mapper;
import com.example.auctionhub.auctionhub.dto.WalletRespose;
import com.example.auctionhub.auctionhub.models.Wallet;

/**
 * MapStruct mapper for converting Wallet entities to WalletRespose DTOs.
 */
@Mapper(componentModel = "spring")
public interface WalletMapper {
    /**
     * Maps a Wallet entity to a WalletRespose DTO.
     *
     * @param wallet Wallet entity to map
     * @return WalletRespose DTO
     */
    WalletRespose toWalletResponse(Wallet wallet);
}
