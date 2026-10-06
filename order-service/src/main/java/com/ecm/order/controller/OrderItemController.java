package com.ecm.order.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.order.dto.response.OrderItemDetailResponse;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.service.OrderItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/order-items")
@RequiredArgsConstructor
public class OrderItemController {
    private final OrderItemRepository orderItemRepository;
    private final OrderItemService orderItemService;

    @GetMapping("/{id}")
    public ApiResponse<OrderItemDetailResponse> getOrderItem(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success(orderItemService.getOwnedOrderItem(id, authentication));
    }

    @GetMapping("/variants/{variantId}/exists")
    public ApiResponse<Boolean> hasOrderHistory(@PathVariable UUID variantId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        if (jwt == null || !jwt.getClaims().containsKey("roles") || !jwt.getClaimAsStringList("roles").contains("ROLE_ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Admin role required");
        }
        return ApiResponse.success(orderItemRepository.existsByProductVariantId(variantId));
    }
}
