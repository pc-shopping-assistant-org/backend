package com.ecm.payment.mapper;

import com.ecm.payment.dto.request.CreatePaymentRequest;
import com.ecm.payment.dto.response.PaymentResponse;
import com.ecm.payment.entity.Payment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    Payment toEntity(CreatePaymentRequest request);

    PaymentResponse toResponse(Payment payment);
}
