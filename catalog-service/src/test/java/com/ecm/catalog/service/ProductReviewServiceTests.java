package com.ecm.catalog.service;

import com.ecm.catalog.client.IdentityServiceClient;
import com.ecm.catalog.client.OrderServiceClient;
import com.ecm.catalog.dto.request.CreateReviewRequest;
import com.ecm.catalog.dto.request.UpdateReviewRequest;
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
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.PageResponse;
import com.ecm.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private IdentityServiceClient identityServiceClient;
    @Mock private ProductReviewMapper reviewMapper;

    private ProductReviewService service;

    private final UUID productId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();
    private final UUID orderItemId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID reviewId = UUID.randomUUID();
    private final UpdateReviewRequest edit = new UpdateReviewRequest(2, "changed");
    private final CreateReviewRequest request = new CreateReviewRequest(orderItemId, RATING, "Great");

    @BeforeEach
    void setUp() {
        service = new ProductReviewService(reviewRepository, productRepository, productVariantRepository,
                orderServiceClient, identityServiceClient, reviewMapper);
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
        ReviewResponse response = new ReviewResponse(UUID.randomUUID(), productId, null, RATING, "Great", Instant.now(), null);
        when(reviewRepository.saveAndFlush(any(ProductReview.class))).thenAnswer(call -> call.getArgument(0));
        when(reviewMapper.toResponse(any(ProductReview.class), eq(null))).thenReturn(response);

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

    @Test
    void listsReviewsWithReviewerNamesFromOneIdentityCall() {
        givenActiveProduct();
        UUID otherCustomer = UUID.randomUUID();
        ProductReview first = review(customerId, Instant.now(), null);
        ProductReview second = review(otherCustomer, Instant.now(), null);
        when(reviewRepository.findByProductIdAndStatusOrderByCreatedAtDesc(eq(productId), eq(CatalogStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(identityServiceClient.getCustomerNames(List.of(customerId, otherCustomer)))
                .thenReturn(ApiResponse.success(Map.of(customerId, "An Nguyen", otherCustomer, "Binh Tran")));
        when(reviewMapper.toResponse(any(ProductReview.class), anyString())).thenAnswer(call -> new ReviewResponse(
                null, productId, call.getArgument(1), RATING, null, null, null));

        PageResponse<ReviewResponse> page = service.getProductReviews(productId, 0, 20);

        assertThat(page.getContent()).extracting(ReviewResponse::reviewerName).containsExactly("An Nguyen", "Binh Tran");
        verify(identityServiceClient).getCustomerNames(any());
    }

    @Test
    void failsTheWholeListWhenIdentityIsDown() {
        givenActiveProduct();
        when(reviewRepository.findByProductIdAndStatusOrderByCreatedAtDesc(eq(productId), eq(CatalogStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(review(customerId, Instant.now(), null))));
        when(identityServiceClient.getCustomerNames(any())).thenThrow(new ExternalServiceException("identity-service", "down"));

        assertThatThrownBy(() -> service.getProductReviews(productId, 0, 20)).isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void skipsTheIdentityCallForAnEmptyPage() {
        givenActiveProduct();
        when(reviewRepository.findByProductIdAndStatusOrderByCreatedAtDesc(eq(productId), eq(CatalogStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.getProductReviews(productId, 0, 20).getContent()).isEmpty();
        verify(identityServiceClient, never()).getCustomerNames(any());
    }

    private ProductReview review(UUID author, Instant createdAt, Instant editedAt) {
        return ProductReview.builder().id(reviewId).productId(productId).customerId(author).rating(RATING).comment("old")
                .status(CatalogStatus.ACTIVE).createdAt(createdAt).editedAt(editedAt).build();
    }

    private void givenReview(ProductReview review) {
        when(reviewRepository.findByIdAndProductIdAndCustomerIdAndStatus(reviewId, productId, customerId, CatalogStatus.ACTIVE))
                .thenReturn(Optional.of(review));
    }

    private void assertEditRejected(CatalogErrorCode code) {
        assertThatThrownBy(() -> service.updateReview(productId, reviewId, edit, customerId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(code);
        verify(reviewRepository, never()).applyEdit(any(), any(), anyInt(), any(), any());
    }

    @Test
    void editsOnceAndKeepsTheFieldsNotSent() {
        ProductReview stored = review(customerId, Instant.now().minus(Duration.ofDays(2)), null);
        givenReview(stored);
        givenActiveProduct();
        when(reviewRepository.applyEdit(eq(reviewId), eq(customerId), eq(2), eq("old"), any())).thenReturn(1);
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(stored));
        ReviewResponse response = new ReviewResponse(reviewId, productId, null, 2, "old", null, Instant.now());
        when(reviewMapper.toResponse(stored, null)).thenReturn(response);

        assertThat(service.updateReview(productId, reviewId, new UpdateReviewRequest(2, null), customerId)).isEqualTo(response);
    }

    @Test
    void hidesAReviewThatIsNotTheCallers() {
        when(reviewRepository.findByIdAndProductIdAndCustomerIdAndStatus(any(), any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateReview(productId, reviewId, edit, customerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsASecondEdit() {
        givenReview(review(customerId, Instant.now().minus(Duration.ofDays(2)), Instant.now().minus(Duration.ofDays(1))));

        assertEditRejected(CatalogErrorCode.REVIEW_ALREADY_EDITED);
    }

    @Test
    void rejectsAnEditAfterThirtyDays() {
        givenReview(review(customerId, Instant.now().minus(Duration.ofDays(31)), null));

        assertEditRejected(CatalogErrorCode.REVIEW_EDIT_WINDOW_EXPIRED);
    }

    @Test
    void rejectsAnEditWhenTheProductIsNoLongerOnSale() {
        givenReview(review(customerId, Instant.now().minus(Duration.ofDays(2)), null));
        when(productRepository.findById(productId))
                .thenReturn(Optional.of(Product.builder().id(productId).status(CatalogStatus.INACTIVE).build()));

        assertEditRejected(CatalogErrorCode.REVIEW_PRODUCT_UNAVAILABLE);
    }

    @Test
    void losesTheRaceWhenAnotherEditLandsFirst() {
        givenReview(review(customerId, Instant.now().minus(Duration.ofDays(2)), null));
        givenActiveProduct();
        when(reviewRepository.applyEdit(any(), any(), anyInt(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.updateReview(productId, reviewId, edit, customerId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CatalogErrorCode.REVIEW_ALREADY_EDITED);
    }
}
