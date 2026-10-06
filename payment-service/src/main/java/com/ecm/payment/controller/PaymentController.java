package com.ecm.payment.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.payment.dto.request.CreatePaymentRequest;
import com.ecm.payment.dto.request.PaymentWebhookRequest;
import com.ecm.payment.dto.response.PaymentResponse;
import com.ecm.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Called by order-service right after stock is reserved (saga step).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        return ApiResponse.success(paymentService.create(request));
    }

    /**
     * Called by the payment gateway (use case 6 in service-communication.md) to report the outcome.
     */
    @PostMapping("/{id}/webhook")
    public ApiResponse<PaymentResponse> webhook(@PathVariable UUID id, @Valid @RequestBody PaymentWebhookRequest request) {
        return ApiResponse.success(paymentService.handleWebhook(id, request));
    }
}
