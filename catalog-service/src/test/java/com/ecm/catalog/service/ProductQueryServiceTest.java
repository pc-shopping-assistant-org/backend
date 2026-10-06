package com.ecm.catalog.service;

import com.ecm.catalog.dto.request.ProductFilterRequest;
import com.ecm.catalog.dto.response.CursorPageResponse;
import com.ecm.catalog.dto.response.ProductSummaryResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Product;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductQueryServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;
    private ProductAssembler assembler;
    private ProductQueryService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        assembler = mock(ProductAssembler.class);
        service = new ProductQueryService(productRepository, mock(ProductVariantRepository.class), categoryRepository, assembler);
        when(assembler.summaries(any(), anyCollection(), anyBoolean())).thenReturn(List.of());
    }

    private static List<Product> products(int count) {
        List<Product> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(Product.builder().id(UUID.randomUUID()).name("P" + i).status(CatalogStatus.ACTIVE).build());
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Pageable> capturePage(List<CatalogStatus> statuses, List<CatalogStatus> variantStatuses) {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).search(eq(statuses), eq(variantStatuses), any(), anyBoolean(), any(), any(), any(), any(), any(), pageable.capture());
        return pageable;
    }

    // ---- UC-ADM-PROD-001 / 002 ----

    @Test
    void publicListSeesOnlyActiveAndOnlyProductsWithVariants() {
        when(productRepository.search(any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any())).thenReturn(products(1));

        service.getProducts(new ProductFilterRequest());

        capturePage(List.of(CatalogStatus.ACTIVE), List.of(CatalogStatus.ACTIVE));
        verify(assembler).summaries(any(), eq(List.of(CatalogStatus.ACTIVE)), eq(true));
    }

    @Test
    void publicListIgnoresTheStatusFilter() {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setStatus(CatalogStatus.INACTIVE);
        when(productRepository.search(any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        service.getProducts(filter);

        capturePage(List.of(CatalogStatus.ACTIVE), List.of(CatalogStatus.ACTIVE));
    }

    @Test
    void adminListShowsActiveAndInactiveAndKeepsProductsWithoutVariants() {
        when(productRepository.search(any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        service.getAdminProducts(new ProductFilterRequest());

        capturePage(List.of(CatalogStatus.ACTIVE, CatalogStatus.INACTIVE), List.of(CatalogStatus.ACTIVE, CatalogStatus.INACTIVE));
        verify(assembler).summaries(any(), anyCollection(), eq(false));
    }

    @Test
    void adminListFiltersByStatusButNeverShowsDeleted() {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setStatus(CatalogStatus.INACTIVE);
        when(productRepository.search(any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        service.getAdminProducts(filter);
        capturePage(List.of(CatalogStatus.INACTIVE), List.of(CatalogStatus.ACTIVE, CatalogStatus.INACTIVE));

        filter.setStatus(CatalogStatus.DELETED);
        assertEquals(CatalogErrorCode.INVALID_PRODUCT_STATUS,
                assertThrows(BusinessException.class, () -> service.getAdminProducts(filter)).getErrorCode());
    }

    @Test
    void keywordIsLowerCasedAndWrappedForLikeSearch() {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setKeyword("  RAM Kit ");
        when(productRepository.search(any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        service.getAdminProducts(filter);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(productRepository).search(any(), any(), any(), anyBoolean(), any(), any(), keyword.capture(), any(), any(), any());
        assertEquals("%ram kit%", keyword.getValue());
    }

    @Test
    void blankKeywordMeansNoKeywordFilter() {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setKeyword("   ");
        when(productRepository.search(any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        service.getAdminProducts(filter);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(productRepository).search(any(), any(), any(), anyBoolean(), any(), any(), keyword.capture(), any(), any(), any());
        assertNull(keyword.getValue());
    }

    @Test
    void invalidPriceRangesAreRejectedBeforeQuerying() {
        for (long[] range : new long[][]{{500, 100}, {-1, 100}, {0, -5}}) {
            ProductFilterRequest filter = new ProductFilterRequest();
            filter.setMinPrice(range[0]);
            filter.setMaxPrice(range[1]);
            assertEquals(CatalogErrorCode.INVALID_PRICE_RANGE,
                    assertThrows(BusinessException.class, () -> service.getAdminProducts(filter)).getErrorCode());
        }
        verify(productRepository, never()).search(any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void readsOneExtraRowToDetectNextPageAndReturnsItsCursor() {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setLimit(2);
        List<Product> three = products(3);
        when(productRepository.search(any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any())).thenReturn(three);
        when(assembler.summaries(any(), anyCollection(), anyBoolean())).thenReturn(List.of(new ProductSummaryResponse(), new ProductSummaryResponse()));

        CursorPageResponse<ProductSummaryResponse> page = service.getAdminProducts(filter);

        assertTrue(page.isHasNext());
        assertEquals(three.get(1).getId().toString(), page.getNextCursor());
        assertEquals(2, page.getSize());
        assertEquals(3, capturePage(List.of(CatalogStatus.ACTIVE, CatalogStatus.INACTIVE), List.of(CatalogStatus.ACTIVE, CatalogStatus.INACTIVE))
                .getValue().getPageSize());
    }

    @Test
    void lastPageHasNoCursor() {
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setLimit(5);
        when(productRepository.search(any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any())).thenReturn(products(2));

        CursorPageResponse<ProductSummaryResponse> page = service.getAdminProducts(filter);

        assertFalse(page.isHasNext());
        assertNull(page.getNextCursor());
    }

    @Test
    void pageSizeIsCappedAndDefaulted() {
        ProductFilterRequest huge = new ProductFilterRequest();
        huge.setLimit(10_000);
        when(productRepository.search(any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        service.getAdminProducts(huge);

        assertEquals(101, capturePage(List.of(CatalogStatus.ACTIVE, CatalogStatus.INACTIVE), List.of(CatalogStatus.ACTIVE, CatalogStatus.INACTIVE))
                .getValue().getPageSize());
    }

    @Test
    void categoryFilterIncludesSubCategories() {
        UUID parent = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setCategoryId(parent);
        when(categoryRepository.findSelfAndDescendantIds(parent)).thenReturn(List.of(parent, child));
        when(productRepository.search(any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        service.getProducts(filter);

        verify(productRepository).search(any(), any(), any(), eq(false), eq(List.of(parent, child)), any(), any(), any(), any(), any());
    }

    @Test
    void noCategoryFilterMatchesEveryCategory() {
        when(productRepository.search(any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        service.getProducts(new ProductFilterRequest());

        verify(productRepository).search(any(), any(), any(), eq(true), eq(List.of()), any(), any(), any(), any(), any());
        verify(categoryRepository, never()).findSelfAndDescendantIds(any());
    }

    // ---- detail ----

    @Test
    void publicDetailHidesInactiveProducts() {
        when(productRepository.findByIdAndStatusIn(eq(PRODUCT_ID), eq(List.of(CatalogStatus.ACTIVE)))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getProductById(PRODUCT_ID));
    }

    @Test
    void adminDetailShowsInactiveProductsWithInactiveVariants() {
        Product product = Product.builder().id(PRODUCT_ID).status(CatalogStatus.INACTIVE).build();
        when(productRepository.findByIdAndStatusIn(eq(PRODUCT_ID), eq(List.of(CatalogStatus.ACTIVE, CatalogStatus.INACTIVE))))
                .thenReturn(Optional.of(product));

        service.getAdminProductById(PRODUCT_ID);

        verify(assembler).detail(product, List.of(CatalogStatus.ACTIVE, CatalogStatus.INACTIVE));
    }
}
