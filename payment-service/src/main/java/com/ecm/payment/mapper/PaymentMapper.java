package com.ecm.payment.mapper;

import com.ecm.payment.dto.request.CreatePaymentRequest;
import com.ecm.payment.dto.response.AdminPaymentMethodResponse;
import com.ecm.payment.dto.response.AdminPaymentResponse;
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

    AdminPaymentResponse toAdminResponse(Payment payment);

    AdminPaymentMethodResponse toAdminMethodResponse(PaymentMethod method);

    List<AdminPaymentMethodResponse> toAdminMethodResponseList(List<PaymentMethod> methods);

    PaymentMethodResponse toMethodResponse(PaymentMethod method);

    List<PaymentMethodResponse> toMethodResponseList(List<PaymentMethod> methods);
}
