package com.ecm.order.client;

import com.ecm.order.dto.request.ApplyDiscountRequest;
import com.ecm.order.dto.response.DiscountApplyResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Called synchronously at checkout — the discount amount must be returned immediately, no eventual consistency. */
@FeignClient(name = "promotion-service")
public interface PromotionServiceClient {

    @PostMapping("/discounts/apply")
    DiscountApplyResponse apply(@RequestBody ApplyDiscountRequest request);
}
