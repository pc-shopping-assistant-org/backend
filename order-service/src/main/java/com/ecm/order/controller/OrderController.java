package com.ecm.order.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.common.security.CurrentUser;
import com.ecm.order.dto.request.CancelOrderRequest;
import com.ecm.order.dto.request.CreateOrderRequest;
import com.ecm.order.dto.response.CursorPageResponse;
import com.ecm.order.dto.response.OrderDetailResponse;
import com.ecm.order.dto.response.OrderStatusResponse;
import com.ecm.order.dto.response.OrderSummaryResponse;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.service.CheckoutService;
import com.ecm.order.service.OrderService;
import com.ecm.order.service.AdminOrderQueryService;
import com.ecm.order.service.InvoiceQueryService;
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
import com.ecm.order.dto.request.AdminInvoiceSearchRequest;
import com.ecm.order.dto.request.AdminOrderSearchRequest;
import com.ecm.order.dto.request.UpdateOrderStatusRequest;
import com.ecm.order.dto.response.AdminOrderDetailResponse;
import com.ecm.order.dto.response.AdminOrderSummaryResponse;
import com.ecm.order.dto.response.InvoiceDetailResponse;
import com.ecm.order.dto.response.InvoiceSummaryResponse;
import com.ecm.order.util.CurrentCustomer;
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
    private final AdminOrderQueryService adminOrderQueryService;
    private final InvoiceQueryService invoiceQueryService;
    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderDetailResponse> create(@Valid @RequestBody CreateOrderRequest request, Authentication authentication) {
        return ApiResponse.success(checkoutService.placeOrder(request, CurrentCustomer.id(authentication), CurrentUser.bearerToken(authentication)));
    }

    @GetMapping
    public ApiResponse<CursorPageResponse<OrderSummaryResponse>> getMyOrders(
            Authentication authentication,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit) {
        if (page != null) {
            return ApiResponse.success(orderQueryService.getOrdersPage(CurrentCustomer.id(authentication), status, keyword, page, limit));
        }
        return ApiResponse.success(orderQueryService.getOrders(CurrentCustomer.id(authentication), status, keyword, cursor, limit));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponse> getMyOrder(@PathVariable UUID orderId, Authentication authentication) {
        return ApiResponse.success(orderQueryService.getDetail(orderId, CurrentCustomer.id(authentication), CurrentUser.bearerToken(authentication)));
    }

    @GetMapping("/{orderId}/status")
    public ApiResponse<OrderStatusResponse> getMyOrderStatus(@PathVariable UUID orderId, Authentication authentication) {
        return ApiResponse.success(orderQueryService.getStatus(orderId, CurrentCustomer.id(authentication)));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderDetailResponse> cancelMyOrder(@PathVariable UUID orderId,
                                                          @Valid @RequestBody(required = false) CancelOrderRequest request,
                                                          Authentication authentication) {
        return ApiResponse.success(orderService.cancelByCustomer(orderId, CurrentCustomer.id(authentication),
                request == null ? null : request.reason()));
    }

    // ---- the shop: every customer's orders, employees only (see SecurityConfig) ----

    @GetMapping("/admin")
    public ApiResponse<PageResponse<AdminOrderSummaryResponse>> getOrders(@Valid @ModelAttribute AdminOrderSearchRequest filter,
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(adminOrderQueryService.searchOrders(filter, page, size));
    }

    @GetMapping("/admin/invoices")
    public ApiResponse<PageResponse<InvoiceSummaryResponse>> getInvoices(@Valid @ModelAttribute AdminInvoiceSearchRequest filter,
                                                                        @RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(invoiceQueryService.searchInvoices(filter, page, size));
    }

    @GetMapping("/admin/invoices/{orderId}")
    public ApiResponse<InvoiceDetailResponse> getInvoice(@PathVariable UUID orderId) {
        return ApiResponse.success(invoiceQueryService.getInvoice(orderId));
    }

    @GetMapping("/admin/{orderId}")
    public ApiResponse<AdminOrderDetailResponse> getOrder(@PathVariable UUID orderId, Authentication authentication) {
        return ApiResponse.success(adminOrderQueryService.getOrderDetail(orderId, CurrentUser.bearerToken(authentication)));
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

}
