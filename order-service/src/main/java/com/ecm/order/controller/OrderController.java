package com.ecm.order.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.common.response.PageResponse;
import com.ecm.order.dto.request.CreateOrderRequest;
import com.ecm.order.dto.response.OrderResponse;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final String CART_SESSION_HEADER = "X-Cart-Session";

    private final OrderService orderService;

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> getMyOrders(
            Authentication authentication,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Get orders successfully",
                orderService.getCustomerOrders(authentication, status, page, size));
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<OrderResponse>> searchMyOrders(
            Authentication authentication,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Search orders successfully",
                orderService.searchCustomerOrders(authentication, keyword, page, size));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getMyOrder(
            @PathVariable UUID orderId, Authentication authentication) {
        return ApiResponse.success("Get order successfully", orderService.getCustomerOrder(orderId, authentication));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancelMyOrder(
            @PathVariable UUID orderId, Authentication authentication) {
        return ApiResponse.success("Order cancelled successfully", orderService.cancelCustomerOrder(orderId, authentication));
    }

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
