package com.ecm.order.controller;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.response.ApiResponse;
import com.ecm.common.security.CurrentUser;
import com.ecm.order.dto.request.AddToCartRequest;
import com.ecm.order.dto.request.UpdateCartItemRequest;
import com.ecm.order.dto.response.CartResponse;
import com.ecm.order.exception.OrderErrorCode;
import com.ecm.order.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ApiResponse<CartResponse> getCart(Authentication authentication) {
        return ApiResponse.success(cartService.getCart(customerId(authentication)));
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addItem(@Valid @RequestBody AddToCartRequest body, Authentication authentication) {
        return ApiResponse.success(cartService.addItem(customerId(authentication), body));
    }

    @PutMapping("/items/{variantId}")
    public ApiResponse<CartResponse> updateItem(@PathVariable UUID variantId, @Valid @RequestBody UpdateCartItemRequest body,
                                                Authentication authentication) {
        return ApiResponse.success(cartService.updateItem(customerId(authentication), variantId, body));
    }

    @DeleteMapping("/items/{variantId}")
    public ApiResponse<CartResponse> removeItem(@PathVariable UUID variantId, Authentication authentication) {
        return ApiResponse.success(cartService.removeItem(customerId(authentication), variantId));
    }

    @DeleteMapping
    public ApiResponse<CartResponse> clearCart(Authentication authentication) {
        return ApiResponse.success(cartService.clearCart(customerId(authentication)));
    }

    private UUID customerId(Authentication authentication) {
        UUID accountId = CurrentUser.accountId(authentication);
        if (accountId == null) {
            throw new BusinessException(OrderErrorCode.CART_OWNER_REQUIRED);
        }
        return accountId;
    }
}
