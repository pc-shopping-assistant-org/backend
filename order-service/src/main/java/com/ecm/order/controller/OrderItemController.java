package com.ecm.order.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.order.dto.request.OrderedVariantsRequest;
import com.ecm.order.dto.response.OrderItemDetailResponse;
import com.ecm.order.service.OrderItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/order-items")
@RequiredArgsConstructor
public class OrderItemController {
    private final OrderItemService orderItemService;

    @GetMapping("/{id}")
    public ApiResponse<OrderItemDetailResponse> getOrderItem(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success(orderItemService.getOwnedOrderItem(id, authentication));
    }

    // Restricted to ROLE_EMPLOYEE by SecurityConfig.
    @PostMapping("/variants/ordered")
    public ApiResponse<List<UUID>> findOrderedVariants(@Valid @RequestBody OrderedVariantsRequest request) {
        return ApiResponse.success(orderItemService.findOrderedVariants(request.variantIds()));
    }
}
