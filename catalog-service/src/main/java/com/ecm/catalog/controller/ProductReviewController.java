package com.ecm.catalog.controller;

import com.ecm.catalog.dto.request.CreateReviewRequest;
import com.ecm.catalog.dto.request.UpdateReviewRequest;
import com.ecm.catalog.dto.response.ReviewResponse;
import com.ecm.catalog.service.ProductReviewService;
import com.ecm.common.response.ApiResponse;
import com.ecm.common.response.PageResponse;
import com.ecm.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/products/{productId}/reviews")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductReviewService reviewService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> create(@PathVariable UUID productId,
                                              @Valid @RequestBody CreateReviewRequest request,
                                              Authentication authentication) {
        return ApiResponse.success(reviewService.createReview(productId, request, CurrentUser.accountId(authentication)));
    }

    @PatchMapping("/{reviewId}")
    public ApiResponse<ReviewResponse> update(@PathVariable UUID productId, @PathVariable UUID reviewId,
                                              @Valid @RequestBody UpdateReviewRequest request,
                                              Authentication authentication) {
        return ApiResponse.success(
                reviewService.updateReview(productId, reviewId, request, CurrentUser.accountId(authentication)));
    }

    @GetMapping
    public ApiResponse<PageResponse<ReviewResponse>> list(@PathVariable UUID productId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(reviewService.getProductReviews(productId, page, size));
    }
}
