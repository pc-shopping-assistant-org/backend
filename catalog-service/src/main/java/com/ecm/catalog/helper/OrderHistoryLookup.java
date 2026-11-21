package com.ecm.catalog.helper;

import com.ecm.catalog.client.OrderServiceClient;
import com.ecm.catalog.dto.request.OrderedVariantsRequest;
import com.ecm.catalog.dto.response.ProductVariantResponse;
import com.ecm.common.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Asks the Order Service, in one call, which variants appear on an order. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderHistoryLookup {

    private final OrderServiceClient orderServiceClient;

    public Set<UUID> orderedAmong(Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> ordered = orderServiceClient.findOrderedVariants(new OrderedVariantsRequest(List.copyOf(variantIds))).getData();
        return ordered == null ? Set.of() : Set.copyOf(ordered);
    }

    /**
     * Fills {@code hasOrders} on each variant of an admin response. The flag only greys out inputs in the admin page
     * (saving checks the order history again), so an unreachable Order Service leaves it unset instead of failing the read.
     */
    public void markOrdered(List<ProductVariantResponse> variants) {
        try {
            Set<UUID> ordered = orderedAmong(variants.stream().map(ProductVariantResponse::getId).toList());
            variants.forEach(variant -> variant.setHasOrders(ordered.contains(variant.getId())));
        } catch (ExternalServiceException ex) {
            log.warn("Order history unavailable, variants shown without hasOrders: {}", ex.getMessage());
        }
    }

    public boolean hasOrders(UUID variantId) {
        return orderedAmong(List.of(variantId)).contains(variantId);
    }
}
