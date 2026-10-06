package com.ecm.payment.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.payment.dto.response.PaymentMethodResponse;
import com.ecm.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentService paymentService;

    /** The methods a customer can pay with at checkout. */
    @GetMapping
    public ApiResponse<List<PaymentMethodResponse>> getActiveMethods() {
        return ApiResponse.success(paymentService.getActivePaymentMethods());
    }
}
