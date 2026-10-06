package com.ecm.catalog.service;

import com.ecm.catalog.client.OrderServiceClient;
import com.ecm.catalog.dto.request.CreateReviewRequest;
import com.ecm.catalog.dto.response.OrderItemResponse;
import com.ecm.catalog.dto.response.ReviewResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Product;
import com.ecm.catalog.entity.ProductReview;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.mapper.ProductReviewMapper;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.catalog.repository.ProductReviewRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductReviewServiceTests {

    private static final String COMPLETED = "COMPLETED";
    private static final int RATING = 5;

    @Mock private ProductReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private OrderServiceClient orderServiceClient;
    @Mock private ProductReviewMapper reviewMapper;

    private ProductReviewService service;

    private final UUID productId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();
    private final UUID orderItemId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final CreateReviewRequest request = new CreateReviewRequest(orderItemId, RATING, "Great");

    @BeforeEach
    void setUp() {
        service = new ProductReviewService(reviewRepository, productRepository, productVariantRepository,
                orderServiceClient, reviewMapper);
    }

    private void givenActiveProduct() {
        when(productRepository.findById(productId))
                .thenReturn(Optional.of(Product.builder().id(productId).status(CatalogStatus.ACTIVE).build()));
    }

    private void givenOrderItem(String orderStatus) {
        when(orderServiceClient.getOrderItem(orderItemId)).thenReturn(ApiResponse.success(
                new OrderItemResponse(orderItemId, UUID.randomUUID(), orderStatus, variantId)));
    }

    private void givenVariantOf(UUID owningProductId) {
        when(productVariantRepository.findById(variantId))
                .thenReturn(Optional.of(ProductVariant.builder().id(variantId).productId(owningProductId).build()));
    }

    @Test
    void createsReviewForCompletedOrderItemOfThisProduct() {
        givenActiveProduct();
        givenOrderItem(COMPLETED);
        givenVariantOf(productId);
        ReviewResponse response = new ReviewResponse(UUID.randomUUID(), productId, RATING, "Great", Instant.now());
        when(reviewRepository.saveAndFlush(any(ProductReview.class))).thenAnswer(call -> call.getArgument(0));
        when(reviewMapper.toResponse(any(ProductReview.class))).thenReturn(response);

        assertThat(service.createReview(productId, request, customerId)).isEqualTo(response);

        verify(reviewRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(review ->
                review.getOrderItemId().equals(orderItemId) && review.getProductId().equals(productId)
                        && review.getCustomerId().equals(customerId) && review.getStatus() == CatalogStatus.ACTIVE));
    }

    @Test
    void rejectsReviewForUnknownProduct() {
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createReview(productId, request, customerId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSecondReviewOfTheSameOrderItem() {
        givenActiveProduct();
        when(reviewRepository.existsByOrderItemId(orderItemId)).thenReturn(true);

        assertThatThrownBy(() -> service.createReview(productId, request, customerId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CatalogErrorCode.REVIEW_ALREADY_EXISTS);
        verify(orderServiceClient, never()).getOrderItem(any());
    }

    @Test
    void reportsNotFoundWhenOrderItemIsNotTheCallers() {
        givenActiveProduct();
        when(orderServiceClient.getOrderItem(orderItemId)).thenThrow(new ResourceNotFoundException("not found"));

        assertThatThrownBy(() -> service.createReview(productId, request, customerId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(orderItemId.toString());
    }

    @Test
    void rejectsReviewWhenOrderIsNotCompleted() {
        givenActiveProduct();
        givenOrderItem("SHIPPING");

        assertThatThrownBy(() -> service.createReview(productId, request, customerId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CatalogErrorCode.REVIEW_ORDER_NOT_COMPLETED);
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsReviewWhenOrderItemIsForAnotherProduct() {
        givenActiveProduct();
        givenOrderItem(COMPLETED);
        givenVariantOf(UUID.randomUUID());

        assertThatThrownBy(() -> service.createReview(productId, request, customerId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CatalogErrorCode.REVIEW_PRODUCT_MISMATCH);
    }

    @Test
    void mapsConcurrentDuplicateToAlreadyReviewed() {
        givenActiveProduct();
        givenOrderItem(COMPLETED);
        givenVariantOf(productId);
        when(reviewRepository.saveAndFlush(any(ProductReview.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.createReview(productId, request, customerId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CatalogErrorCode.REVIEW_ALREADY_EXISTS);
    }

    @Test
    void rejectsInvalidPagingWhenListing() {
        assertThatThrownBy(() -> service.getProductReviews(productId, -1, 10)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getProductReviews(productId, 0, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getProductReviews(productId, 0, 1000)).isInstanceOf(BusinessException.class);
    }
}
