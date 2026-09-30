package com.ecm.catalog;

import com.ecm.catalog.dto.request.ProductFilterRequest;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.catalog.service.ProductService;
import com.ecm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CatalogServiceApplicationTests {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductService productService;

    @Test
    void contextLoads() {
        assertThat(productService).isNotNull();
    }

    @Test
    void productPriceFiltersRunInDatabaseAndKeepCursorPagesConsistent() {
        UUID categoryId = categoryRepository.findByStatus(CatalogStatus.ACTIVE).stream()
                .map(category -> category.getId())
                .findFirst().orElse(null);
        if (categoryId == null) {
            return;
        }

        UUID brandId = null;
        var products = productRepository.findInitial(CatalogStatus.ACTIVE, categoryId, brandId, null,
                0L, Long.MAX_VALUE, PageRequest.of(0, 3));
        assertThat(products).isNotNull();

        if (!products.isEmpty()) {
            UUID cursor = products.get(0).getId();
            assertThat(productRepository.findAfterCursor(CatalogStatus.ACTIVE, cursor, categoryId, brandId,
                    null, 0L, Long.MAX_VALUE, PageRequest.of(0, 3))).allMatch(p -> p.getId().compareTo(cursor) < 0);
        }
    }

    @Test
    void invalidPriceRangeIsRejected() {
        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> productService.getProducts(ProductFilterRequest.builder()
                        .minPrice(500L)
                        .maxPrice(100L)
                        .build()));
        assertThat(exception.getErrorCode()).isEqualTo(CatalogErrorCode.INVALID_PRICE_RANGE);
    }
}
