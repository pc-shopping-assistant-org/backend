package com.ecm.catalog.util;

import com.ecm.catalog.dto.request.VariantOptionRequest;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.common.exception.BusinessException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Rules on the options sent with a variant. */
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
            if (!names.add(option.name().trim().toUpperCase(Locale.ROOT))) {
                throw new BusinessException(CatalogErrorCode.INVALID_VARIANT_OPTIONS,
                        "Variant has more than one option named " + option.name().trim());
            }
        }
    }
}
