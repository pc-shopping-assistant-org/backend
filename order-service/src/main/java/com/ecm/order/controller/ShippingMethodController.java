package com.ecm.order.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.order.dto.response.ShippingMethodResponse;
import com.ecm.order.service.ShippingMethodService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/shipping-methods")
@RequiredArgsConstructor
public class ShippingMethodController {

    private final ShippingMethodService shippingMethodService;

    /** The methods a customer can ship an order with at checkout. */
    @GetMapping
    public ApiResponse<List<ShippingMethodResponse>> getActiveMethods() {
        return ApiResponse.success(shippingMethodService.getActiveMethods());
    }
}
