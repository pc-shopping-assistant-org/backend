package com.ecm.catalog.service;

import com.ecm.catalog.dto.request.ProductFilterRequest;
import com.ecm.catalog.dto.response.CursorPageResponse;
import com.ecm.catalog.dto.response.ProductDetailResponse;
import com.ecm.catalog.dto.response.ProductSummaryResponse;
import com.ecm.catalog.dto.response.ProductVariantResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Product;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Product reads. The public storefront sees only ACTIVE products with ACTIVE variants; the admin views
 * (UC-ADM-PROD-001/002) also see INACTIVE ones and can filter by status.
 */
@Service
@RequiredArgsConstructor
public class ProductQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final List<CatalogStatus> PUBLIC_STATUSES = List.of(CatalogStatus.ACTIVE);

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductAssembler assembler;

    @Transactional(readOnly = true)
    public CursorPageResponse<ProductSummaryResponse> getProducts(ProductFilterRequest filter) {
        return page(filter, PUBLIC_STATUSES, PUBLIC_STATUSES, true);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ProductSummaryResponse> getAdminProducts(ProductFilterRequest filter) {
        if (filter.getStatus() == CatalogStatus.DELETED) {
            throw new BusinessException(CatalogErrorCode.INVALID_PRODUCT_STATUS);
        }
        List<CatalogStatus> statuses = filter.getStatus() == null ? ProductService.ADMIN_VISIBLE_STATUSES : List.of(filter.getStatus());
        return page(filter, statuses, ProductService.ADMIN_VISIBLE_STATUSES, false);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProductById(UUID id) {
        Product product = productRepository.findByIdAndStatusIn(id, PUBLIC_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return assembler.detail(product, PUBLIC_STATUSES);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getAdminProductById(UUID id) {
        Product product = productRepository.findByIdAndStatusIn(id, ProductService.ADMIN_VISIBLE_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return assembler.detail(product, ProductService.ADMIN_VISIBLE_STATUSES);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProductBySeoName(String seoName) {
        Product product = productRepository.findBySeoNameAndStatus(seoName, CatalogStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Product", seoName));
        return assembler.detail(product, PUBLIC_STATUSES);
    }

    @Transactional(readOnly = true)
    public ProductVariantResponse getProductVariant(UUID productId, UUID variantId) {
        ProductVariant variant = productVariantRepository.findByIdAndStatus(variantId, CatalogStatus.ACTIVE)
                .filter(existing -> existing.getProductId().equals(productId))
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));
        return assembler.variant(variant);
    }

    private CursorPageResponse<ProductSummaryResponse> page(ProductFilterRequest filter, List<CatalogStatus> productStatuses,
                                                            List<CatalogStatus> variantStatuses, boolean onlyWithVariants) {
        // 1. Validate the price range and sanitize the page size and keyword
        validatePriceRange(filter);
        int pageSize = filter.getLimit() != null && filter.getLimit() > 0 ? Math.min(filter.getLimit(), MAX_LIMIT) : DEFAULT_LIMIT;
        String keywordPattern = filter.getKeyword() != null && !filter.getKeyword().isBlank()
                ? "%" + filter.getKeyword().trim().toLowerCase(Locale.ROOT) + "%"
                : null;

        // 2. A category filter also matches the products of its sub-categories
        boolean anyCategory = filter.getCategoryId() == null;
        List<UUID> categoryIds = anyCategory ? List.of() : categoryRepository.findSelfAndDescendantIds(filter.getCategoryId());

        // 3. Read one extra row to learn whether another page follows
        List<Product> products = productRepository.search(productStatuses, variantStatuses, filter.getCursor(),
                anyCategory, categoryIds, filter.getBrandId(), keywordPattern, filter.getMinPrice(), filter.getMaxPrice(),
                PageRequest.of(0, pageSize + 1));
        boolean hasNext = products.size() > pageSize;
        List<Product> results = hasNext ? products.subList(0, pageSize) : products;
        String nextCursor = hasNext ? results.get(results.size() - 1).getId().toString() : null;

        // 4. Assemble the summaries
        List<ProductSummaryResponse> items = assembler.summaries(results, variantStatuses, onlyWithVariants);
        return CursorPageResponse.<ProductSummaryResponse>builder()
                .items(items).nextCursor(nextCursor).hasNext(hasNext).size(items.size()).build();
    }

    private void validatePriceRange(ProductFilterRequest filter) {
        Long minPrice = filter.getMinPrice();
        Long maxPrice = filter.getMaxPrice();
        if ((minPrice != null && minPrice < 0)
                || (maxPrice != null && maxPrice < 0)
                || (minPrice != null && maxPrice != null && minPrice > maxPrice)) {
            throw new BusinessException(CatalogErrorCode.INVALID_PRICE_RANGE);
        }
    }
}
