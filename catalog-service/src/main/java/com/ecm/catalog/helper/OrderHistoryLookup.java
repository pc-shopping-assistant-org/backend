package com.ecm.catalog.helper;

import com.ecm.catalog.client.OrderServiceClient;
import com.ecm.catalog.dto.request.OrderedVariantsRequest;
import com.ecm.catalog.dto.response.ProductVariantResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Asks the Order Service, in one call, which variants appear on an order. */
@Component
@RequiredArgsConstructor
public class OrderHistoryLookup {

    private final OrderServiceClient orderServiceClient;

    public Set<UUID> orderedAmong(Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> ordered = orderServiceClient.findOrderedVariants(new OrderedVariantsRequest(List.copyOf(variantIds))).getData();
        return ordered == null ? Set.of() : Set.copyOf(ordered);
    }

    /** Fills {@code hasOrders} on each variant of an admin response. */
    public void markOrdered(List<ProductVariantResponse> variants) {
        Set<UUID> ordered = orderedAmong(variants.stream().map(ProductVariantResponse::getId).toList());
        variants.forEach(variant -> variant.setHasOrders(ordered.contains(variant.getId())));
    }

    public boolean hasOrders(UUID variantId) {
        return orderedAmong(List.of(variantId)).contains(variantId);
    }
}
