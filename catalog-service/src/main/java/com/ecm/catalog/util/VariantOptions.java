package com.ecm.catalog.util;

import com.ecm.catalog.dto.request.VariantOptionRequest;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.common.exception.BusinessException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Rules on the options of variants. A combination is the options of one variant as name -> value, both trimmed and
 * upper-cased, so "Color: Black" and " color : black" are the same combination.
 */
public final class VariantOptions {

    private VariantOptions() {
    }

    /** A variant may hold only one option per option name, compared ignoring case and surrounding whitespace. */
    public static void ensureUniqueNames(List<VariantOptionRequest> options) {
        if (options == null) {
            return;
        }
        Set<String> names = new HashSet<>();
        for (VariantOptionRequest option : options) {
            if (!names.add(normalized(option.name()))) {
                throw new BusinessException(CatalogErrorCode.INVALID_VARIANT_OPTIONS,
                        "Variant has more than one option named " + option.name().trim());
            }
        }
    }

    public static String normalized(String text) {
        return text.trim().toUpperCase(Locale.ROOT);
    }

    /** The combination of the options sent with a variant; no options is the empty combination of a product without groups. */
    public static Map<String, String> combination(List<VariantOptionRequest> options) {
        Map<String, String> combination = new HashMap<>();
        if (options != null) {
            options.forEach(option -> combination.put(normalized(option.name()), normalized(option.value())));
        }
        return combination;
    }

    /**
     * An ACTIVE variant must use the same option names as the other ACTIVE variants of its product, so the storefront
     * can offer one choice per name, and a combination none of them has, so every choice leads to one variant. With no
     * other ACTIVE variant, this one sets the names.
     */
    public static void ensureFitsProduct(Map<String, String> combination, List<Map<String, String>> others) {
        if (others.isEmpty()) {
            return;
        }
        Set<String> names = others.get(0).keySet();
        if (!combination.keySet().equals(names)) {
            throw new BusinessException(CatalogErrorCode.VARIANT_OPTIONS_MISMATCH, names.isEmpty()
                    ? "This product has no option groups; change its variations to add one"
                    : "Every variant of this product must have exactly the options " + String.join(", ", new TreeSet<>(names)));
        }
        if (others.contains(combination)) {
            throw new BusinessException(CatalogErrorCode.DUPLICATE_VARIANT_COMBINATION, combination.isEmpty()
                    ? "A product without option groups can have only one active variant"
                    : "Another variant already has the options " + combination);
        }
    }

    /** {@link #ensureFitsProduct} for a whole list of ACTIVE variants at once. */
    public static void ensureConsistent(List<Map<String, String>> combinations) {
        for (int i = 0; i < combinations.size(); i++) {
            ensureFitsProduct(combinations.get(i), combinations.subList(0, i));
        }
    }

    /**
     * True when an option name the variant keeps would get another value, which would make it another item ("Black"
     * becoming "Grey"). Adding or dropping an option name keeps the item the same.
     */
    public static boolean changesValues(Map<String, String> before, Map<String, String> after) {
        return before.entrySet().stream()
                .anyMatch(option -> after.containsKey(option.getKey()) && !after.get(option.getKey()).equals(option.getValue()));
    }
}
