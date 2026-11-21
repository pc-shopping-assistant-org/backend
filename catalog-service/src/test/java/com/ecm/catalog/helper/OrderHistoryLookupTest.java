package com.ecm.catalog.helper;

import com.ecm.catalog.client.OrderServiceClient;
import com.ecm.catalog.dto.response.ProductVariantResponse;
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.common.response.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderHistoryLookupTest {

    private final OrderServiceClient orderServiceClient = mock(OrderServiceClient.class);
    private final OrderHistoryLookup lookup = new OrderHistoryLookup(orderServiceClient);

    private static ProductVariantResponse variant(UUID id) {
        ProductVariantResponse variant = new ProductVariantResponse();
        variant.setId(id);
        return variant;
    }

    @Test
    void marksTheVariantsThatWereOrdered() {
        UUID ordered = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        when(orderServiceClient.findOrderedVariants(any())).thenReturn(ApiResponse.success(List.of(ordered)));
        List<ProductVariantResponse> variants = List.of(variant(ordered), variant(fresh));

        lookup.markOrdered(variants);

        assertEquals(true, variants.get(0).getHasOrders());
        assertEquals(false, variants.get(1).getHasOrders());
    }

    @Test
    void leavesTheFlagUnsetWhenOrderServiceIsDown() {
        when(orderServiceClient.findOrderedVariants(any())).thenThrow(new ExternalServiceException("order-service", "down"));
        List<ProductVariantResponse> variants = List.of(variant(UUID.randomUUID()));

        lookup.markOrdered(variants);

        assertNull(variants.get(0).getHasOrders());
    }

    @Test
    void writeChecksStillFailWhenOrderServiceIsDown() {
        when(orderServiceClient.findOrderedVariants(any())).thenThrow(new ExternalServiceException("order-service", "down"));

        assertThrows(ExternalServiceException.class, () -> lookup.hasOrders(UUID.randomUUID()));
    }
}
