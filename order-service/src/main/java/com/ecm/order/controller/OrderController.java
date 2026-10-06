package com.ecm.order.controller;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.response.ApiResponse;
import com.ecm.common.security.CurrentUser;
import com.ecm.order.dto.request.CancelOrderRequest;
import com.ecm.order.dto.request.CreateOrderRequest;
import com.ecm.order.dto.response.CursorPageResponse;
import com.ecm.order.dto.response.OrderDetailResponse;
import com.ecm.order.dto.response.OrderStatusResponse;
import com.ecm.order.dto.response.OrderSummaryResponse;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.exception.OrderErrorCode;
import com.ecm.order.service.CheckoutService;
import com.ecm.order.service.OrderCancellationService;
import com.ecm.order.service.OrderQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The orders of the logged-in customer. */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final CheckoutService checkoutService;
    private final OrderQueryService orderQueryService;
    private final OrderCancellationService orderCancellationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderDetailResponse> create(@Valid @RequestBody CreateOrderRequest request, Authentication authentication) {
        return ApiResponse.success(checkoutService.placeOrder(request, customerId(authentication), bearerToken(authentication)));
    }

    @GetMapping
    public ApiResponse<CursorPageResponse<OrderSummaryResponse>> getMyOrders(
            Authentication authentication,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(orderQueryService.getOrders(customerId(authentication), status, keyword, cursor, limit));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponse> getMyOrder(@PathVariable UUID orderId, Authentication authentication) {
        return ApiResponse.success(orderQueryService.getDetail(orderId, customerId(authentication), bearerToken(authentication)));
    }

    @GetMapping("/{orderId}/status")
    public ApiResponse<OrderStatusResponse> getMyOrderStatus(@PathVariable UUID orderId, Authentication authentication) {
        return ApiResponse.success(orderQueryService.getStatus(orderId, customerId(authentication)));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderDetailResponse> cancelMyOrder(@PathVariable UUID orderId,
                                                          @Valid @RequestBody(required = false) CancelOrderRequest request,
                                                          Authentication authentication) {
        return ApiResponse.success(orderCancellationService.cancelByCustomer(orderId, customerId(authentication),
                request == null ? null : request.reason()));
    }

    private UUID customerId(Authentication authentication) {
        UUID accountId = CurrentUser.accountId(authentication);
        if (accountId == null) {
            throw new BusinessException(OrderErrorCode.CART_OWNER_REQUIRED);
        }
        return accountId;
    }

    /** The token of the caller, relayed to the services the order has to ask on behalf of the customer. */
    private String bearerToken(Authentication authentication) {
        return BEARER_PREFIX + ((JwtAuthenticationToken) authentication).getToken().getTokenValue();
    }
}
