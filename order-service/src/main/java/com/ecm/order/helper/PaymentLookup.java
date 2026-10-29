package com.ecm.order.helper;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.order.client.PaymentServiceClient;
import com.ecm.order.dto.response.PaymentMethodResponse;
import com.ecm.order.dto.response.PaymentResponse;
import com.ecm.order.exception.OrderErrorCode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** What the Payment Service knows: the methods the shop offers and the payment attempts of an order. */
@Component
@RequiredArgsConstructor
public class PaymentLookup {

    private static final String PAYMENT_SERVICE = "payment-service";

    private final PaymentServiceClient paymentServiceClient;

    /** The payment method an order is placed with, among the ones the shop offers. */
    public PaymentMethodResponse method(UUID paymentMethodId, String bearerToken) {
        List<PaymentMethodResponse> methods;
        try {
            methods = Objects.requireNonNull(paymentServiceClient.getPaymentMethods(bearerToken).getData());
        } catch (FeignException ex) {
            throw new ExternalServiceException(PAYMENT_SERVICE, ex);
        }
        return methods.stream().filter(method -> method.id().equals(paymentMethodId)).findFirst()
                .orElseThrow(() -> new BusinessException(OrderErrorCode.INVALID_PAYMENT_METHOD));
    }

    public List<PaymentResponse> forOrder(UUID orderId, String bearerToken) {
        try {
            return Objects.requireNonNull(paymentServiceClient.getPaymentsByOrder(orderId, bearerToken).getData());
        } catch (FeignException ex) {
            throw new ExternalServiceException(PAYMENT_SERVICE, ex);
        }
    }
}
