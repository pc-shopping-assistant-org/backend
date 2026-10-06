package com.ecm.identity.dto.response;

import java.util.UUID;

public record AddressResponse(
        UUID id,
        String recipientName,
        String phone,
        String addressLine,
        boolean isDefault) {
}
