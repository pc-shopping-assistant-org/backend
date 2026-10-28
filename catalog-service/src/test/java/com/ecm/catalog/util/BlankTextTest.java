package com.ecm.catalog.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlankTextTest {

    @Test
    void absentAndBlankTextBecomeNull() {
        assertNull(BlankText.trimmedOrNull(null));
        assertNull(BlankText.trimmedOrNull("   "));
    }

    @Test
    void otherTextLosesTheSpacesAroundIt() {
        assertEquals("8935001", BlankText.trimmedOrNull("  8935001 "));
    }
}
