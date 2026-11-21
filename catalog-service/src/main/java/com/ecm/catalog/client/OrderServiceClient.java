package com.ecm.catalog.client;

import com.ecm.catalog.dto.request.OrderedVariantsRequest;
import com.ecm.catalog.dto.response.OrderItemResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

/**
 * Called before accepting a product review, to confirm the order item belongs to the reviewer and the order is COMPLETED,
 * and before deleting or changing variants, to know which of them have been ordered.
 */
@FeignClient(name = "order-service")
public interface OrderServiceClient {

    @GetMapping("/order-items/{id}")
    com.ecm.common.response.ApiResponse<OrderItemResponse> getOrderItem(@PathVariable("id") UUID id);

    @PostMapping("/order-items/variants/ordered")
    com.ecm.common.response.ApiResponse<List<UUID>> findOrderedVariants(@RequestBody OrderedVariantsRequest request);
}
