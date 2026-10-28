package com.ecm.sync.util;

import com.ecm.sync.exception.InvalidChangeEventException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangeRowsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsTextAndIdColumns() throws Exception {
        UUID id = UUID.randomUUID();
        JsonNode row = mapper.readTree("{\"id\":\"" + id + "\",\"name\":\"ASUS\"}");

        assertEquals(id, ChangeRows.uuid(row, "id"));
        assertEquals("ASUS", ChangeRows.text(row, "name"));
    }

    @Test
    void aMissingNullOrMalformedColumnInvalidatesTheEvent() throws Exception {
        JsonNode row = mapper.readTree("{\"id\":\"not-an-id\",\"name\":null}");

        assertThrows(InvalidChangeEventException.class, () -> ChangeRows.text(row, "name"));
        assertThrows(InvalidChangeEventException.class, () -> ChangeRows.text(row, "other"));
        assertThrows(InvalidChangeEventException.class, () -> ChangeRows.uuid(row, "id"));
    }
}
