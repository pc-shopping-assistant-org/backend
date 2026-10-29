package com.ecm.order.util;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/** Where the previous page of the orders of a customer stopped: the creation time and id of its last order. */
public record OrderCursor(Instant createdAt, UUID id) {

    /** Sorts after every order, so a list without a cursor starts at the newest one. */
    public static final OrderCursor START = new OrderCursor(Instant.parse("9999-12-31T00:00:00Z"), new UUID(-1L, -1L));

    private static final String SEPARATOR = "|";

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((createdAt + SEPARATOR + id).getBytes(StandardCharsets.UTF_8));
    }

    public static OrderCursor decode(String cursor) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\" + SEPARATOR);
            return new OrderCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException | DateTimeParseException ex) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Invalid cursor");
        }
    }
}
