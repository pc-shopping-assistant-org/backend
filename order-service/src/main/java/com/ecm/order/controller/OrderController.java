package com.ecm.order.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.order.dto.request.CreateOrderRequest;
import com.ecm.order.dto.response.OrderResponse;
import com.ecm.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final String CART_SESSION_HEADER = "X-Cart-Session";

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication,
            @RequestHeader(value = CART_SESSION_HEADER, required = false) String sessionToken) {
        return ApiResponse.success("Order created successfully",
                orderService.createOrder(request, authentication, sessionToken));
    }
}
