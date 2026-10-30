package com.ecm.catalog.util;

import com.ecm.catalog.dto.request.VariantOptionRequest;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static Map<String, String> combination(String... nameValues) {
        Map<String, String> combination = new HashMap<>();
        for (int i = 0; i < nameValues.length; i += 2) {
            combination.put(VariantOptions.normalized(nameValues[i]), VariantOptions.normalized(nameValues[i + 1]));
        }
        return combination;
    }

    @Test
    void combinationIgnoresCaseAndSurroundingSpaces() {
        assertEquals(combination("Color", "Black"), VariantOptions.combination(List.of(new VariantOptionRequest(" color ", "BLACK "))));
        assertTrue(VariantOptions.combination(null).isEmpty());
    }

    @Test
    void theFirstActiveVariantSetsTheOptionNames() {
        assertDoesNotThrow(() -> VariantOptions.ensureFitsProduct(combination("Color", "Black"), List.of()));
    }

    @Test
    void otherOptionNamesOrADuplicateCombinationAreRejected() {
        List<Map<String, String>> others = List.of(combination("Color", "Black", "RAM", "16GB"));

        assertEquals(CatalogErrorCode.VARIANT_OPTIONS_MISMATCH, assertThrows(BusinessException.class,
                () -> VariantOptions.ensureFitsProduct(combination("Color", "White"), others)).getErrorCode());
        assertEquals(CatalogErrorCode.DUPLICATE_VARIANT_COMBINATION, assertThrows(BusinessException.class,
                () -> VariantOptions.ensureFitsProduct(combination("ram", "16gb", "color", "black"), others)).getErrorCode());
        assertDoesNotThrow(() -> VariantOptions.ensureFitsProduct(combination("Color", "White", "RAM", "16GB"), others));
    }

    @Test
    void aProductWithoutGroupsHasOneActiveVariant() {
        assertEquals(CatalogErrorCode.DUPLICATE_VARIANT_COMBINATION, assertThrows(BusinessException.class,
                () -> VariantOptions.ensureConsistent(List.of(Map.of(), Map.of()))).getErrorCode());
        assertDoesNotThrow(() -> VariantOptions.ensureConsistent(List.of(Map.of())));
    }

    @Test
    void onlyAnotherValueForAKeptNameChangesTheItem() {
        Map<String, String> black = combination("Color", "Black");

        assertTrue(VariantOptions.changesValues(black, combination("Color", "Grey")));
        assertFalse(VariantOptions.changesValues(black, combination("Color", "Black", "RAM", "16GB")));
        assertFalse(VariantOptions.changesValues(black, Map.of()));
        assertFalse(VariantOptions.changesValues(Map.of(), black));
    }
}
