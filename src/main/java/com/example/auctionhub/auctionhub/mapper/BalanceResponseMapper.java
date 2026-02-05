package com.example.auctionhub.auctionhub.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.auctionhub.auctionhub.controller.WalletController.BalanceResponse;
import com.example.auctionhub.auctionhub.dto.WalletRespose;

/**
 * MapStruct mapper for converting WalletRespose to BalanceResponse.
 */
@Mapper(componentModel = "spring")
public interface BalanceResponseMapper {

    /**
     * Maps WalletRespose to BalanceResponse.
     * Calculates totalBalance as the sum of availableBalance and frozenBalance.
     *
     * @param walletRespose WalletRespose to map from
     * @return BalanceResponse with balance details
     */
    @Mapping(target = "totalBalance", expression = "java(walletRespose.getAvailableBalance().add(walletRespose.getFrozenBalance()))")
    BalanceResponse toBalanceResponse(WalletRespose walletRespose);
}
