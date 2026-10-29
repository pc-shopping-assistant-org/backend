package com.ecm.order.util;

import java.util.UUID;
import java.util.regex.Pattern;

/** Reads the keyword a customer types to find an order. */
public final class OrderKeywords {

    /** Matches no order, for a search that has no order id in it. */
    public static final UUID NO_ORDER_ID = new UUID(0L, 0L);

    private static final Pattern UNDASHED_UUID = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Pattern DASHED_UUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private OrderKeywords() {
    }

    /** The order id the keyword spells, with or without dashes; null when it is not an id, e.g. an invoice number. */
    public static UUID orderId(String keyword) {
        if (DASHED_UUID.matcher(keyword).matches()) {
            return UUID.fromString(keyword);
        }
        if (UNDASHED_UUID.matcher(keyword).matches()) {
            return UUID.fromString(keyword.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
        }
        return null;
    }
}
