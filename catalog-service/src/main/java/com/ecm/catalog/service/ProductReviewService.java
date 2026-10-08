package com.ecm.catalog.service;

import com.ecm.catalog.client.IdentityServiceClient;
import com.ecm.catalog.client.OrderServiceClient;
import com.ecm.catalog.dto.request.CreateReviewRequest;
import com.ecm.catalog.dto.request.UpdateReviewRequest;
import com.ecm.catalog.dto.response.OrderItemResponse;
import com.ecm.catalog.dto.response.ReviewResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.ProductReview;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.mapper.ProductReviewMapper;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.catalog.repository.ProductReviewRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.DuplicateResourceException;
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.PageResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductReviewService {

    private static final String RESOURCE_PRODUCT = "Product";
    private static final String RESOURCE_REVIEW = "Review";
    private static final String IDENTITY_SERVICE = "identity-service";
    private static final String RESOURCE_ORDER_ITEM = "Order item";
    private static final String ORDER_STATUS_COMPLETED = "COMPLETED";
    private static final int MAX_PAGE_SIZE = 50;
    private static final Duration EDIT_WINDOW = Duration.ofDays(30);

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderServiceClient orderServiceClient;
    private final IdentityServiceClient identityServiceClient;
    private final ProductReviewMapper reviewMapper;

    @Transactional
    public ReviewResponse createReview(UUID productId, CreateReviewRequest request, UUID customerId) {
        // 1. The product must exist and not be deleted.
        requireProduct(productId);

        // 2. Reject a second review for the same order line early (the unique index is the final guard).
        if (reviewRepository.existsByOrderItemId(request.orderItemId())) {
            throw alreadyReviewed();
        }

        // 3. Ask order-service for the line; it only returns lines owned by the caller.
        OrderItemResponse orderItem = fetchOwnedOrderItem(request.orderItemId());

        // 4. The order must be completed and the line must be for a variant of this product.
        if (!ORDER_STATUS_COMPLETED.equals(orderItem.orderStatus())) {
            throw new BusinessException(CatalogErrorCode.REVIEW_ORDER_NOT_COMPLETED);
        }
        boolean belongsToProduct = productVariantRepository.findById(orderItem.productVariantId())
                .map(variant -> variant.getProductId().equals(productId))
                .orElse(false);
        if (!belongsToProduct) {
            throw new BusinessException(CatalogErrorCode.REVIEW_PRODUCT_MISMATCH);
        }

        // 5. Persist; a concurrent duplicate surfaces as a unique-constraint violation.
        ProductReview review = ProductReview.builder()
                .orderItemId(request.orderItemId())
                .productId(productId)
                .customerId(customerId)
                .rating(request.rating())
                .comment(request.comment())
                .status(CatalogStatus.ACTIVE)
                .build();
        try {
            return reviewMapper.toResponse(reviewRepository.saveAndFlush(review), null);
        } catch (DataIntegrityViolationException ex) {
            throw alreadyReviewed();
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getProductReviews(UUID productId, int page, int size) {
        // 1. Validate paging and the product.
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        requireProduct(productId);

        // 2. Newest active reviews first.
        Page<ProductReview> reviews = reviewRepository
                .findByProductIdAndStatusOrderByCreatedAtDesc(productId, CatalogStatus.ACTIVE, PageRequest.of(page, size));

        // 3. One identity call for the whole page; a failure surfaces as 503 rather than a list with missing names.
        Map<UUID, String> names = reviewerNames(reviews.getContent());
        return PageResponse.of(reviews.map(review -> reviewMapper.toResponse(review, names.get(review.getCustomerId()))));
    }

    @Transactional
    public ReviewResponse updateReview(UUID productId, UUID reviewId, UpdateReviewRequest request, UUID customerId) {
        // 1. Only the author sees the review; anything else is "not found".
        ProductReview review = reviewRepository
                .findByIdAndProductIdAndCustomerIdAndStatus(reviewId, productId, customerId, CatalogStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_REVIEW, reviewId));

        // 2. One edit, within the window, while the product is still on sale.
        Instant now = Instant.now();
        if (review.getEditedAt() != null) {
            throw new BusinessException(CatalogErrorCode.REVIEW_ALREADY_EDITED);
        }
        if (now.isAfter(review.getCreatedAt().plus(EDIT_WINDOW))) {
            throw new BusinessException(CatalogErrorCode.REVIEW_EDIT_WINDOW_EXPIRED);
        }
        boolean onSale = productRepository.findById(productId)
                .map(product -> product.getStatus() == CatalogStatus.ACTIVE)
                .orElse(false);
        if (!onSale) {
            throw new BusinessException(CatalogErrorCode.REVIEW_PRODUCT_UNAVAILABLE);
        }

        // 3. Conditional update keeps two concurrent edits from both winning.
        int rating = request.rating() != null ? request.rating() : review.getRating();
        String comment = request.comment() != null ? request.comment() : review.getComment();
        if (reviewRepository.applyEdit(reviewId, customerId, rating, comment, now) == 0) {
            throw new BusinessException(CatalogErrorCode.REVIEW_ALREADY_EDITED);
        }

        // 4. Answer with the stored row.
        return reviewMapper.toResponse(reviewRepository.findById(reviewId).orElseThrow(), null);
    }

    private Map<UUID, String> reviewerNames(List<ProductReview> reviews) {
        if (reviews.isEmpty()) {
            return Map.of();
        }
        List<UUID> customerIds = reviews.stream().map(ProductReview::getCustomerId).distinct().toList();
        try {
            return identityServiceClient.getCustomerNames(customerIds).getData();
        } catch (ResourceNotFoundException | FeignException ex) {
            throw new ExternalServiceException(IDENTITY_SERVICE, ex.getMessage());
        }
    }

    private void requireProduct(UUID productId) {
        productRepository.findById(productId)
                .filter(product -> product.getStatus() != CatalogStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_PRODUCT, productId));
    }

    private OrderItemResponse fetchOwnedOrderItem(UUID orderItemId) {
        try {
            return orderServiceClient.getOrderItem(orderItemId).getData();
        } catch (ResourceNotFoundException ex) {
            throw new ResourceNotFoundException(RESOURCE_ORDER_ITEM, orderItemId);
        }
    }

    private DuplicateResourceException alreadyReviewed() {
        return new DuplicateResourceException(CatalogErrorCode.REVIEW_ALREADY_EXISTS,
                CatalogErrorCode.REVIEW_ALREADY_EXISTS.getDefaultMessage());
    }
}
