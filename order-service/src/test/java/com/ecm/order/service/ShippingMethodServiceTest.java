package com.ecm.order.service;

import com.ecm.order.dto.response.ShippingMethodResponse;
import com.ecm.order.entity.ShippingMethod;
import com.ecm.order.entity.ShippingMethodStatus;
import com.ecm.order.mapper.ShippingMethodMapperImpl;
import com.ecm.order.repository.ShippingMethodRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShippingMethodServiceTest {

    @Test
    void listsTheActiveMethodsWithTheirCurrentFee() {
        ShippingMethodRepository repository = mock(ShippingMethodRepository.class);
        UUID id = UUID.randomUUID();
        when(repository.findByStatusOrderByFeeAscNameAsc(ShippingMethodStatus.ACTIVE))
                .thenReturn(List.of(ShippingMethod.builder().id(id).code("STANDARD").name("Standard Delivery").fee(30_000L)
                        .status(ShippingMethodStatus.ACTIVE).build()));

        List<ShippingMethodResponse> methods = new ShippingMethodService(repository, new ShippingMethodMapperImpl()).getActiveMethods();

        assertEquals(List.of(new ShippingMethodResponse(id, "STANDARD", "Standard Delivery", 30_000L)), methods);
    }
}
