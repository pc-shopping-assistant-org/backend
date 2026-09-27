package com.ecm.identity.dto.response;

import java.util.UUID;

public record UserSummaryResponse(
        UUID accountId,
        String email,
        String phone,
        String role,
        String firstName,
        String lastName) {
}
