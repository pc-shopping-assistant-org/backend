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
import com.ecm.order.service.OrderService;
import com.ecm.order.service.OrderQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ecm.common.response.PageResponse;
import com.ecm.order.dto.request.AdminCancelOrderRequest;
import com.ecm.order.dto.request.AdminOrderSearchRequest;
import com.ecm.order.dto.request.UpdateOrderStatusRequest;
import com.ecm.order.dto.response.AdminOrderDetailResponse;
import com.ecm.order.dto.response.AdminOrderSummaryResponse;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import java.util.UUID;

/** Orders: the logged-in customer's own, and under {@code /orders/admin} those of every customer for the shop. */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CheckoutService checkoutService;
    private final OrderQueryService orderQueryService;
    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderDetailResponse> create(@Valid @RequestBody CreateOrderRequest request, Authentication authentication) {
        return ApiResponse.success(checkoutService.placeOrder(request, customerId(authentication), CurrentUser.bearerToken(authentication)));
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
        return ApiResponse.success(orderQueryService.getDetail(orderId, customerId(authentication), CurrentUser.bearerToken(authentication)));
    }

    @GetMapping("/{orderId}/status")
    public ApiResponse<OrderStatusResponse> getMyOrderStatus(@PathVariable UUID orderId, Authentication authentication) {
        return ApiResponse.success(orderQueryService.getStatus(orderId, customerId(authentication)));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderDetailResponse> cancelMyOrder(@PathVariable UUID orderId,
                                                          @Valid @RequestBody(required = false) CancelOrderRequest request,
                                                          Authentication authentication) {
        return ApiResponse.success(orderService.cancelByCustomer(orderId, customerId(authentication),
                request == null ? null : request.reason()));
    }

    // ---- the shop: every customer's orders, employees only (see SecurityConfig) ----

    @GetMapping("/admin")
    public ApiResponse<PageResponse<AdminOrderSummaryResponse>> getOrders(@Valid @ModelAttribute AdminOrderSearchRequest filter,
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(orderQueryService.searchOrders(filter, page, size));
    }

    @GetMapping("/admin/{orderId}")
    public ApiResponse<AdminOrderDetailResponse> getOrder(@PathVariable UUID orderId, Authentication authentication) {
        return ApiResponse.success(orderQueryService.getOrderDetail(orderId, CurrentUser.bearerToken(authentication)));
    }

    @PatchMapping("/admin/{orderId}/status")
    public ApiResponse<OrderStatusResponse> updateStatus(@PathVariable UUID orderId, @Valid @RequestBody UpdateOrderStatusRequest request,
                                                         Authentication authentication) {
        return ApiResponse.success(orderService.updateStatus(orderId, request.status(), CurrentUser.accountId(authentication)));
    }

    @PostMapping("/admin/{orderId}/cancel")
    public ApiResponse<OrderStatusResponse> cancelOrder(@PathVariable UUID orderId, @Valid @RequestBody AdminCancelOrderRequest request,
                                                        Authentication authentication) {
        return ApiResponse.success(orderService.cancelByEmployee(orderId, CurrentUser.accountId(authentication), request.reason()));
    }

    private UUID customerId(Authentication authentication) {
        UUID accountId = CurrentUser.accountId(authentication);
        if (accountId == null) {
            throw new BusinessException(OrderErrorCode.CART_OWNER_REQUIRED);
        }
        return accountId;
    }
}
