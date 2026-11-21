package com.ecm.catalog.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductVariantTest {

    private static final UUID EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    private static ProductVariant onSale(int quantity) {
        return ProductVariant.builder().quantity(quantity).status(CatalogStatus.ACTIVE).build();
    }

    @Test
    void retireDeletesAVariantWithoutStockOrOrders() {
        ProductVariant variant = onSale(0);
        Instant now = Instant.now();

        variant.retire(false, now, EMPLOYEE_ID);

        assertEquals(CatalogStatus.DELETED, variant.getStatus());
        assertEquals(now, variant.getUpdatedAt());
        assertEquals(EMPLOYEE_ID, variant.getUpdatedBy());
    }

    @Test
    void retireHidesAVariantWithStockOrOrders() {
        ProductVariant withStock = onSale(3);
        ProductVariant ordered = onSale(0);

        withStock.retire(false, Instant.now(), EMPLOYEE_ID);
        ordered.retire(true, Instant.now(), EMPLOYEE_ID);

        assertEquals(CatalogStatus.INACTIVE, withStock.getStatus());
        assertEquals(CatalogStatus.INACTIVE, ordered.getStatus());
    }
}
