package com.ecm.payment.mapper;

import com.ecm.payment.dto.request.CreatePaymentRequest;
import com.ecm.payment.dto.response.PaymentMethodResponse;
import com.ecm.payment.dto.response.PaymentResponse;
import com.ecm.payment.entity.Payment;
import com.ecm.payment.entity.PaymentMethod;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    Payment toEntity(CreatePaymentRequest request);

    PaymentResponse toResponse(Payment payment);

    List<PaymentResponse> toResponseList(List<Payment> payments);

    PaymentMethodResponse toMethodResponse(PaymentMethod method);

    List<PaymentMethodResponse> toMethodResponseList(List<PaymentMethod> methods);
}
