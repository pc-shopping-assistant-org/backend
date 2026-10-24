package com.ecm.identity.dto.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegisterRequestNormalizedTest {

    @Test
    void trimsTheIdentityFieldsAndLowerCasesTheEmail() {
        RegisterRequest request = new RegisterRequest(
                "  An@Example.COM ", " 0900000001 ", " keep-as-typed ", " An ", " Nguyen ", null, null, " 1 Le Loi ");

        RegisterRequest normalized = request.normalized();

        assertEquals("an@example.com", normalized.email());
        assertEquals("0900000001", normalized.phone());
        assertEquals(" keep-as-typed ", normalized.password());
        assertEquals("An", normalized.firstName());
        assertEquals("Nguyen", normalized.lastName());
        assertEquals("1 Le Loi", normalized.address());
    }
}
