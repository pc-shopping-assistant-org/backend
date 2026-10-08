package com.ecm.promotion.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.promotion.client.CatalogServiceClient;
import com.ecm.promotion.exception.PromotionErrorCode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Asks the Catalog Service, which owns the categories, whether the categories a discount targets are in use. */
@Component
@RequiredArgsConstructor
public class DiscountCategoryChecker {

    private static final String CATALOG_SERVICE = "catalog-service";

    private final CatalogServiceClient catalogServiceClient;

    /** Fails with INVALID_DISCOUNT_CATEGORY when any of the categories is unknown or not in use. */
    public void requireInUse(Collection<UUID> categoryIds) {
        Set<UUID> inUse;
        try {
            inUse = catalogServiceClient.getActiveCategories().getData().stream()
                    .map(CatalogServiceClient.CategoryRef::id).collect(Collectors.toSet());
        } catch (FeignException ex) {
            throw new ExternalServiceException(CATALOG_SERVICE, ex);
        }
        if (!inUse.containsAll(categoryIds)) {
            throw new BusinessException(PromotionErrorCode.INVALID_DISCOUNT_CATEGORY);
        }
    }
}
