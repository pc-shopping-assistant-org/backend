package com.ecm.sync.util;

import com.ecm.sync.exception.InvalidChangeEventException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/** Reads the columns of one changed row of a change event; a missing or malformed column makes the event invalid. */
public final class ChangeRows {

    private ChangeRows() {
    }

    public static UUID uuid(JsonNode row, String column) {
        try {
            return UUID.fromString(text(row, column));
        } catch (IllegalArgumentException ex) {
            throw new InvalidChangeEventException("Column " + column + " is not an id: " + row.get(column), ex);
        }
    }

    public static String text(JsonNode row, String column) {
        JsonNode value = row.get(column);
        if (value == null || value.isNull()) {
            throw new InvalidChangeEventException("Row has no column " + column, null);
        }
        return value.asText();
    }
}
