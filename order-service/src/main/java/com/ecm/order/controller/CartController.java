package com.ecm.order.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.common.security.CurrentUser;
import com.ecm.order.dto.request.AddToCartRequest;
import com.ecm.order.dto.request.UpdateCartItemRequest;
import com.ecm.order.dto.response.CartResponse;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private static final String CART_SESSION_HEADER = "X-Cart-Session";

    private final CartService cartService;

    @GetMapping
    public ApiResponse<CartResponse> getCart(
            Authentication authentication,
            @RequestHeader(value = CART_SESSION_HEADER, required = false) String sessionToken) {
        return ApiResponse.success("Get cart successfully", cartService.getCart(accountId(authentication), sessionToken));
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addItem(
            @Valid @RequestBody AddToCartRequest body,
            Authentication authentication,
            @RequestHeader(value = CART_SESSION_HEADER, required = false) String sessionToken) {
        return ApiResponse.success("Cart item added successfully",
                cartService.addItem(accountId(authentication), sessionToken, body));
    }

    @PutMapping("/items/{variantId}")
    public ApiResponse<CartResponse> updateItem(
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateCartItemRequest body,
            Authentication authentication,
            @RequestHeader(value = CART_SESSION_HEADER, required = false) String sessionToken) {
        return ApiResponse.success("Cart item updated successfully",
                cartService.updateItem(accountId(authentication), sessionToken, variantId, body));
    }

    @DeleteMapping("/items/{variantId}")
    public ApiResponse<CartResponse> removeItem(
            @PathVariable UUID variantId,
            Authentication authentication,
            @RequestHeader(value = CART_SESSION_HEADER, required = false) String sessionToken) {
        return ApiResponse.success("Cart item removed successfully",
                cartService.removeItem(accountId(authentication), sessionToken, variantId));
    }

    @DeleteMapping
    public ApiResponse<CartResponse> clearCart(
            Authentication authentication,
            @RequestHeader(value = CART_SESSION_HEADER, required = false) String sessionToken) {
        return ApiResponse.success("Cart cleared successfully",
                cartService.clearCart(accountId(authentication), sessionToken));
    }

    @PostMapping("/merge")
    public ApiResponse<CartResponse> mergeGuestCart(
            Authentication authentication,
            @RequestHeader(CART_SESSION_HEADER) String sessionToken) {
        return ApiResponse.success("Guest cart merged successfully",
                cartService.mergeGuestCart(accountId(authentication), sessionToken));
    }

    private UUID accountId(Authentication authentication) {
        return CurrentUser.accountId(authentication);
    }
}
