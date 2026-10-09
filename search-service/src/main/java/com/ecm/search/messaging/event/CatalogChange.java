package com.ecm.search.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The part of a Debezium change event this service reads. {@code before} is null for an insert and
 * {@code after} is null for a delete; the schema-less JSON converter is configured on the connector.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogChange(
        @JsonProperty("before") JsonNode before,
        @JsonProperty("after") JsonNode after,
        @JsonProperty("source") Source source) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(@JsonProperty("table") String table) {
    }

    /** The row as it is now, or as it was when the change deleted it. */
    public JsonNode row() {
        return after != null && !after.isNull() ? after : before;
    }

    public String table() {
        return source == null ? null : source.table();
    }
}
