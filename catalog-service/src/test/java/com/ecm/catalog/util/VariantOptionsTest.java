package com.ecm.catalog.util;

import com.ecm.catalog.dto.request.VariantOptionRequest;
import com.ecm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VariantOptionsTest {

    @Test
    void noOptionsAndDistinctNamesPass() {
        assertDoesNotThrow(() -> VariantOptions.ensureUniqueNames(null));
        assertDoesNotThrow(() -> VariantOptions.ensureUniqueNames(List.of(
                new VariantOptionRequest("Color", "Black"), new VariantOptionRequest("Size", "M"))));
    }

    @Test
    void theSameNameTwiceIsRejectedIgnoringCaseAndSpaces() {
        List<VariantOptionRequest> options = List.of(new VariantOptionRequest("Color", "Black"), new VariantOptionRequest(" color ", "White"));

        assertThrows(BusinessException.class, () -> VariantOptions.ensureUniqueNames(options));
    }
}
