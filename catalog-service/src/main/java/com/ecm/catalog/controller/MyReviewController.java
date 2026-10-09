package com.ecm.catalog.controller;

import com.ecm.catalog.dto.response.ReviewedOrderItemResponse;
import com.ecm.catalog.service.ProductReviewService;
import com.ecm.common.response.ApiResponse;
import com.ecm.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class MyReviewController {

    private final ProductReviewService reviewService;

    /** Which of these order lines the caller has reviewed (ROLE_CUSTOMER, see SecurityConfig). */
    @GetMapping("/mine")
    public ApiResponse<List<ReviewedOrderItemResponse>> reviewedOrderItems(@RequestParam List<UUID> orderItemIds,
                                                                          Authentication authentication) {
        return ApiResponse.success(reviewService.getReviewedOrderItems(orderItemIds, CurrentUser.accountId(authentication)));
    }
}
