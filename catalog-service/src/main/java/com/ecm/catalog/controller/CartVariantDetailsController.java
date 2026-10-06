package com.ecm.catalog.controller;

import com.ecm.catalog.dto.response.CartVariantDetailsResponse;
import com.ecm.catalog.service.CartVariantDetailsService;
import com.ecm.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cart-variant-details")
@RequiredArgsConstructor
public class CartVariantDetailsController {
    private final CartVariantDetailsService service;

    @GetMapping
    public ApiResponse<List<CartVariantDetailsResponse>> getDetails(@RequestParam List<UUID> ids) {
        return ApiResponse.success(service.getDetails(ids));
    }
}
