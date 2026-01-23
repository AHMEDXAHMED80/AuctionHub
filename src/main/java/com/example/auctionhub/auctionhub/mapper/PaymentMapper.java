package com.example.auctionhub.auctionhub.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.example.auctionhub.auctionhub.dto.PaymentDetailsResponse;
import com.example.auctionhub.auctionhub.dto.PaymentResponse;
import com.example.auctionhub.auctionhub.models.Payment;

/**
 * MapStruct mapper for converting Payment entities to PaymentResponse and PaymentDetailsResponse DTOs.
 */
@Mapper(componentModel = "spring")
public interface PaymentMapper {

    /**
     * Maps a Payment entity to a PaymentResponse DTO.
     *
     * @param payment Payment entity to map
     * @return PaymentResponse DTO
     */
    @Mapping(source = "paymentStatus", target = "status")
    PaymentResponse toPaymentResponse(Payment payment);

    /**
     * Maps a Payment entity to a PaymentDetailsResponse DTO.
     *
     * @param payment Payment entity to map
     * @return PaymentDetailsResponse DTO
     */
    @Mapping(source = "paymentStatus", target = "status")
    PaymentDetailsResponse toPaymentDetailsResponse(Payment payment);
}
