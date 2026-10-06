package com.ecm.order.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.common.response.PageResponse;
import com.ecm.order.dto.request.CreateOrderRequest;
import com.ecm.order.dto.request.AdminOrderSearchRequest;
import com.ecm.order.dto.request.UpdateOrderStatusRequest;
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

    @GetMapping("/admin")
    public ApiResponse<PageResponse<OrderResponse>> getAllOrders(
            @Valid @ModelAttribute AdminOrderSearchRequest filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(orderService.getAdminOrders(filter, page, size));
    }

    @PatchMapping("/admin/{orderId}/status")
    public ApiResponse<OrderResponse> updateOrderStatus(@PathVariable UUID orderId,
                                                         @Valid @RequestBody UpdateOrderStatusRequest request,
                                                         Authentication authentication) {
        UUID employeeId = null;
        if (authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt) {
            try { employeeId = UUID.fromString(jwt.getToken().getSubject()); } catch (IllegalArgumentException ignored) { }
        }
        return ApiResponse.success(orderService.updateAdminOrderStatus(orderId, request.status(), employeeId));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> getMyOrders(
            Authentication authentication,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(orderService.getCustomerOrders(authentication, status, page, size));
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<OrderResponse>> searchMyOrders(
            Authentication authentication,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(orderService.searchCustomerOrders(authentication, keyword, page, size));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getMyOrder(
            @PathVariable UUID orderId, Authentication authentication) {
        return ApiResponse.success(orderService.getCustomerOrder(orderId, authentication));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancelMyOrder(
            @PathVariable UUID orderId, Authentication authentication) {
        return ApiResponse.success(orderService.cancelCustomerOrder(orderId, authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication,
            @RequestHeader(value = CART_SESSION_HEADER, required = false) String sessionToken) {
        return ApiResponse.success(orderService.createOrder(request, authentication, sessionToken));
    }
}
