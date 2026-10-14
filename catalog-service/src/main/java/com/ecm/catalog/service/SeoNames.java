package com.ecm.catalog.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Builds the URL slug of a name; Vietnamese letters are reduced to their plain Latin form instead of being dropped. */
final class SeoNames {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASH = Pattern.compile("^-|-$");

    private SeoNames() {
    }

    /** The given SEO name when there is one, otherwise the slug of the name. */
    static String slug(String seoName, String name) {
        String source = seoName == null || seoName.isBlank() ? name : seoName;
        String plain = DIACRITICS.matcher(Normalizer.normalize(source.trim(), Normalizer.Form.NFD)).replaceAll("")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        return EDGE_DASH.matcher(NON_SLUG.matcher(plain).replaceAll("-")).replaceAll("");
    }
}
