package com.ecm.payment.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.common.response.PageResponse;
import com.ecm.common.security.CurrentUser;
import com.ecm.payment.dto.request.AdminPaymentSearchRequest;
import com.ecm.payment.dto.request.CreatePaymentRequest;
import com.ecm.payment.dto.request.PaymentWebhookRequest;
import com.ecm.payment.dto.request.UpdatePaymentStatusRequest;
import com.ecm.payment.dto.response.AdminPaymentResponse;
import com.ecm.payment.dto.response.PaymentResponse;
import com.ecm.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final String EMPLOYEE_ROLE = "EMPLOYEE";

    private final PaymentService paymentService;

    /**
     * Called by order-service right after stock is reserved (saga step).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        return ApiResponse.success(paymentService.create(request));
    }

    /** The payment attempts of an order, oldest first: all of them for an employee, only the caller own for a customer. */
    @GetMapping("/by-order/{orderId}")
    public ApiResponse<List<PaymentResponse>> getByOrder(@PathVariable UUID orderId, Authentication authentication) {
        UUID customerId = CurrentUser.hasRole(authentication, EMPLOYEE_ROLE) ? null : CurrentUser.accountId(authentication);
        return ApiResponse.success(paymentService.getPaymentsByOrder(orderId, customerId));
    }

    /**
     * Called by the payment gateway (use case 6 in service-communication.md) to report the outcome.
     */
    @PostMapping("/{id}/webhook")
    public ApiResponse<PaymentResponse> webhook(@PathVariable UUID id, @Valid @RequestBody PaymentWebhookRequest request) {
        return ApiResponse.success(paymentService.handleWebhook(id, request));
    }

    // ---- the shop: employees only (see SecurityConfig) ----

    @GetMapping("/admin")
    public ApiResponse<PageResponse<AdminPaymentResponse>> searchPayments(@Valid @ModelAttribute AdminPaymentSearchRequest filter,
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "50") int size,
                                                                         Authentication authentication) {
        return ApiResponse.success(paymentService.searchPayments(filter, page, size, CurrentUser.bearerToken(authentication)));
    }

    @PatchMapping("/admin/{id}/status")
    public ApiResponse<AdminPaymentResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdatePaymentStatusRequest request,
                                                          Authentication authentication) {
        return ApiResponse.success(paymentService.updateStatus(id, request.status(), CurrentUser.accountId(authentication)));
    }
}
