package com.ecm.order.client;

import com.ecm.order.dto.request.CreatePaymentRequest;
import com.ecm.order.dto.response.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Saga step: called right after stock is reserved to start a payment attempt for the order.
 */
@FeignClient(name = "payment-service")
public interface PaymentServiceClient {

    @PostMapping("/payments")
    PaymentResponse create(@RequestBody CreatePaymentRequest request);
}
