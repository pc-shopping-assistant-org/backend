package com.ecm.order.client;

import com.ecm.common.response.ApiResponse;
import com.ecm.order.dto.request.CreatePaymentRequest;
import com.ecm.order.dto.response.PaymentMethodResponse;
import com.ecm.order.dto.response.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "payment-service")
public interface PaymentServiceClient {

    /** Saga step: called once the stock of an order is reserved, to start its payment. */
    @PostMapping("/payments")
    ApiResponse<PaymentResponse> create(@RequestBody CreatePaymentRequest request);

    @GetMapping("/payment-methods")
    ApiResponse<List<PaymentMethodResponse>> getPaymentMethods(@RequestHeader("Authorization") String authorization);

    @GetMapping("/payments/by-order/{orderId}")
    ApiResponse<List<PaymentResponse>> getPaymentsByOrder(@PathVariable("orderId") UUID orderId,
                                                          @RequestHeader("Authorization") String authorization);
}
