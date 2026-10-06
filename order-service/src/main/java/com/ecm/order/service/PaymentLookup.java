package com.ecm.order.service;

import com.ecm.common.exception.ExternalServiceException;
import com.ecm.order.client.PaymentServiceClient;
import com.ecm.order.dto.response.PaymentResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The payment attempts of an order, which live in the Payment Service. */
@Component
@RequiredArgsConstructor
public class PaymentLookup {

    private static final String PAYMENT_SERVICE = "payment-service";

    private final PaymentServiceClient paymentServiceClient;

    public List<PaymentResponse> forOrder(UUID orderId, String bearerToken) {
        try {
            return Objects.requireNonNull(paymentServiceClient.getPaymentsByOrder(orderId, bearerToken).getData());
        } catch (FeignException ex) {
            throw new ExternalServiceException(PAYMENT_SERVICE, ex);
        }
    }
}
