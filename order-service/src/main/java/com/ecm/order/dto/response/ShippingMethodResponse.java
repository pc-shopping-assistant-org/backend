package com.ecm.order.dto.response;

import java.util.UUID;

/** A way to ship an order that a customer can choose at checkout; {@code fee} is what it costs now. */
public record ShippingMethodResponse(UUID id, String code, String name, Long fee) {
}
