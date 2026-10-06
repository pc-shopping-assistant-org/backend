package com.ecm.order.service;

import com.ecm.common.exception.ExternalServiceException;
import com.ecm.order.client.CatalogServiceClient;
import com.ecm.order.dto.response.CartVariantDetailsResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Reads the current price, stock and sale status of variants from the Catalog Service, which owns them. */
@Component
@RequiredArgsConstructor
public class CatalogVariantLookup {

    private static final String CATALOG_SERVICE = "catalog-service";

    private final CatalogServiceClient catalogServiceClient;

    /** One call for all the variants; a variant the Catalog Service does not know is left out. */
    public List<CartVariantDetailsResponse> details(List<UUID> variantIds) {
        try {
            List<CartVariantDetailsResponse> details = catalogServiceClient.getCartVariantDetails(variantIds).getData();
            return details == null ? List.of() : details;
        } catch (FeignException ex) {
            throw new ExternalServiceException(CATALOG_SERVICE, ex);
        }
    }
}
