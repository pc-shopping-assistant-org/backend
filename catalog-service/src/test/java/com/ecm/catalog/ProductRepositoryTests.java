package com.ecm.catalog;

import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Product;
import com.ecm.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Sql(statements = {
        "INSERT INTO categories (id, name, seo_name, status) VALUES ('00000000-0000-0000-0000-000000000101', 'F2 category A', 'f2-category-a', 'ACTIVE'), ('00000000-0000-0000-0000-000000000102', 'F2 category B', 'f2-category-b', 'ACTIVE')",
        "INSERT INTO products (id, name, seo_name, category_id, status) VALUES ('00000000-0000-0000-0000-000000000201', 'Low price product', 'f2-low-price', '00000000-0000-0000-0000-000000000101', 'ACTIVE'), ('00000000-0000-0000-0000-000000000202', 'High price product', 'f2-high-price', '00000000-0000-0000-0000-000000000101', 'ACTIVE'), ('00000000-0000-0000-0000-000000000203', 'Other category product', 'f2-other-category', '00000000-0000-0000-0000-000000000102', 'ACTIVE')",
        "INSERT INTO product_variants (id, product_id, list_price, quantity, sku, warranty_months, status, created_by) VALUES ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000201', 50, 2, 'F2-LOW', 12, 'ACTIVE', '00000000-0000-0000-0000-000000000401'), ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000202', 500, 3, 'F2-HIGH', 12, 'ACTIVE', '00000000-0000-0000-0000-000000000401'), ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000203', 50, 1, 'F2-OTHER', 12, 'ACTIVE', '00000000-0000-0000-0000-000000000401')"
})
class ProductRepositoryTests {

    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID LOW_PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID HIGH_PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Autowired
    private ProductRepository productRepository;

    @Test
    void filtersProductsByCategoryAndPriceRange() {
        var products = productRepository.findInitial(CatalogStatus.ACTIVE, CATEGORY_ID, null, null,
                100L, 1000L, PageRequest.of(0, 10));

        assertThat(products).extracting(Product::getId).containsExactly(HIGH_PRODUCT_ID);
    }

    @Test
    void cursorPaginationReturnsOnlyProductsBeforeCursor() {
        var products = productRepository.findAfterCursor(CatalogStatus.ACTIVE, HIGH_PRODUCT_ID,
                null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(products).extracting(Product::getId).containsExactly(LOW_PRODUCT_ID);
    }

    @Test
    void priceRangeBoundsAreAppliedToSameVariant() {
        var products = productRepository.findInitial(CatalogStatus.ACTIVE, OTHER_CATEGORY_ID, null, null,
                100L, 1000L, PageRequest.of(0, 10));

        assertThat(products).isEmpty();
    }
}
