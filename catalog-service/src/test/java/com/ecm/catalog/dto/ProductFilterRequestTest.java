package com.ecm.catalog.dto;

import com.ecm.catalog.dto.request.ProductFilterRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductFilterRequestTest {

    @Test
    void openAndOrderedRangesAreValid() {
        assertFalse(ProductFilterRequest.builder().build().hasInvalidPriceRange());
        assertFalse(ProductFilterRequest.builder().minPrice(0L).maxPrice(0L).build().hasInvalidPriceRange());
        assertFalse(ProductFilterRequest.builder().minPrice(100L).build().hasInvalidPriceRange());
    }

    @Test
    void negativePricesAndAMinimumAboveTheMaximumAreInvalid() {
        assertTrue(ProductFilterRequest.builder().minPrice(-1L).build().hasInvalidPriceRange());
        assertTrue(ProductFilterRequest.builder().maxPrice(-1L).build().hasInvalidPriceRange());
        assertTrue(ProductFilterRequest.builder().minPrice(200L).maxPrice(100L).build().hasInvalidPriceRange());
    }
}
