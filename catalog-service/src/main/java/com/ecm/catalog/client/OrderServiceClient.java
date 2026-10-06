package com.ecm.catalog.client;

import com.ecm.catalog.dto.response.OrderItemResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Called before accepting a product review, to confirm the order item belongs to the reviewer and the order is COMPLETED.
 */
@FeignClient(name = "order-service")
public interface OrderServiceClient {

    @GetMapping("/order-items/{id}")
    com.ecm.common.response.ApiResponse<OrderItemResponse> getOrderItem(@PathVariable("id") UUID id);

    @GetMapping("/order-items/variants/{variantId}/exists")
    com.ecm.common.response.ApiResponse<Boolean> hasOrderHistory(@PathVariable("variantId") UUID variantId);
}
