package com.ecm.order.dto.response;

import java.util.UUID;

/** A saved address of the customer, as the Identity Service reports it. */
public record AddressResponse(UUID id, String recipientName, String phone, String addressLine) {
}
