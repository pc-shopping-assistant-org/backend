package com.ecm.identity.service.google;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoogleIdentityTest {

    private static GoogleIdentity identity(String given, String family, String full) {
        return new GoogleIdentity("sub", "nguyen.van.a@example.com", given, family, full, null);
    }

    @Test
    void usesTheNamesGoogleHolds() {
        GoogleIdentity identity = identity("An", "Nguyen", "Nguyen Van An");

        assertEquals("An", identity.firstName());
        assertEquals("Nguyen", identity.lastName());
    }

    @Test
    void splitsTheFullNameWhenGoogleHoldsNoSeparateNames() {
        GoogleIdentity identity = identity(null, null, "Nguyen Van An");

        assertEquals("An", identity.firstName());
        assertEquals("Nguyen Van", identity.lastName());
    }

    @Test
    void fallsBackToTheEmailLocalPartWhenThereIsNoName() {
        GoogleIdentity identity = identity(null, null, null);

        assertEquals("nguyen.van.a", identity.firstName());
        assertEquals("", identity.lastName());
    }

    @Test
    void cutsANameTooLongForTheColumn() {
        GoogleIdentity identity = identity("a".repeat(150), null, null);

        assertEquals(100, identity.firstName().length());
    }
}
