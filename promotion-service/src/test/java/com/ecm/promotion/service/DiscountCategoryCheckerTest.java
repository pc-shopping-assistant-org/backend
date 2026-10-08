package com.ecm.promotion.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.common.response.ApiResponse;
import com.ecm.promotion.client.CatalogServiceClient;
import com.ecm.promotion.client.CatalogServiceClient.CategoryRef;
import com.ecm.promotion.exception.PromotionErrorCode;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscountCategoryCheckerTest {

    private static final UUID IN_USE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-0000000000a9");

    private CatalogServiceClient client;
    private DiscountCategoryChecker checker;

    @BeforeEach
    void setUp() {
        client = mock(CatalogServiceClient.class);
        checker = new DiscountCategoryChecker(client);
    }

    @Test
    void acceptsCategoriesThatAreInUse() {
        when(client.getActiveCategories()).thenReturn(ApiResponse.success(List.of(new CategoryRef(IN_USE))));

        assertDoesNotThrow(() -> checker.requireInUse(Set.of(IN_USE)));
    }

    @Test
    void rejectsWhenAnyCategoryIsNotInUse() {
        when(client.getActiveCategories()).thenReturn(ApiResponse.success(List.of(new CategoryRef(IN_USE))));

        BusinessException ex = assertThrows(BusinessException.class, () -> checker.requireInUse(Set.of(IN_USE, UNKNOWN)));

        assertEquals(PromotionErrorCode.INVALID_DISCOUNT_CATEGORY, ex.getErrorCode());
    }

    @Test
    void aFailedCatalogCallIsReportedAsAnExternalServiceFailure() {
        when(client.getActiveCategories()).thenThrow(mock(FeignException.class));

        assertThrows(ExternalServiceException.class, () -> checker.requireInUse(Set.of(IN_USE)));
    }
}
