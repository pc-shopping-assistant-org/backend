package com.ecm.order.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.common.response.PageResponse;
import com.ecm.common.security.CurrentUser;
import com.ecm.order.dto.request.AdminOrderSearchRequest;
import com.ecm.order.dto.request.UpdateOrderStatusRequest;
import com.ecm.order.dto.response.OrderDetailResponse;
import com.ecm.order.dto.response.OrderSummaryResponse;
import com.ecm.order.service.AdminOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/orders/admin")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @GetMapping
    public ApiResponse<PageResponse<OrderSummaryResponse>> getOrders(@Valid @ModelAttribute AdminOrderSearchRequest filter,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(adminOrderService.getOrders(filter, page, size));
    }

    @PatchMapping("/{orderId}/status")
    public ApiResponse<OrderDetailResponse> updateStatus(@PathVariable UUID orderId, @Valid @RequestBody UpdateOrderStatusRequest request,
                                                         Authentication authentication) {
        return ApiResponse.success(adminOrderService.updateStatus(orderId, request.status(), CurrentUser.accountId(authentication)));
    }
}
