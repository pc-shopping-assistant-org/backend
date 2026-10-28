package com.ecm.catalog.util;

/** Optional text fields arrive blank as often as absent; both are stored as null. */
public final class BlankText {

    private BlankText() {
    }

    /** The text without surrounding whitespace, or null when it is null or blank. */
    public static String trimmedOrNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
