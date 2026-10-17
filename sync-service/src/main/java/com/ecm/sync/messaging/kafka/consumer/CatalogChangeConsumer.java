package com.ecm.sync.messaging.kafka.consumer;

import com.ecm.sync.exception.InvalidChangeEventException;
import com.ecm.sync.messaging.event.CatalogChange;
import com.ecm.sync.service.ProductIndexService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * {@code catalog.changed} is fed by Debezium CDC on catalog-service's DB, not by application code (see
 * docs/02-architecture/service-communication.md, use case 10). Every event is one changed row of one table, so each
 * is turned into "which product document is stale now" and answered by re-reading that product from the catalog.
 * Re-indexing is idempotent, so Kafka's at-least-once delivery needs no inbox.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogChangeConsumer {

    public static final String TOPIC = "catalog.changed";
    static final String TABLE_PRODUCTS = "products";
    static final String TABLE_PRODUCT_VARIANTS = "product_variants";
    static final String TABLE_PRODUCT_IMAGES = "product_images";
    static final String TABLE_BRANDS = "brands";
    static final String TABLE_CATEGORIES = "categories";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_PRODUCT_ID = "product_id";
    private static final String COLUMN_NAME = "name";

    private final ProductIndexService indexService;
    private final ObjectMapper objectMapper;

    /**
     * A record that cannot be read throws {@link InvalidChangeEventException}, which goes straight to the dead-letter
     * topic; any other failure (catalog or Elasticsearch unavailable) is retried until it recovers.
     */
    @KafkaListener(topics = TOPIC, groupId = "sync-service")
    public void onCatalogChanged(String payload) {
        // 1. Debezium also sends a null-valued tombstone after a delete; there is nothing in it to act on
        if (payload == null) {
            return;
        }
        CatalogChange change = parse(payload);
        JsonNode row = change.row();
        if (change.table() == null || row == null || row.isNull()) {
            return;
        }

        // 2. A changed row makes one product document stale, or renames a brand inside many, or moves a category with its products
        switch (change.table()) {
            case TABLE_PRODUCTS -> indexService.reindexProduct(uuid(row, COLUMN_ID));
            case TABLE_PRODUCT_VARIANTS, TABLE_PRODUCT_IMAGES -> indexService.reindexProduct(uuid(row, COLUMN_PRODUCT_ID));
            case TABLE_BRANDS -> renameBrand(change, row);
            case TABLE_CATEGORIES -> reindexCategory(change, row);
            default -> log.debug("Ignoring a change of table {}", change.table());
        }
    }

    private CatalogChange parse(String payload) {
        try {
            return objectMapper.readValue(payload, CatalogChange.class);
        } catch (JsonProcessingException ex) {
            throw new InvalidChangeEventException("Unreadable catalog change: " + ex.getOriginalMessage(), ex);
        }
    }

    private void renameBrand(CatalogChange change, JsonNode row) {
        if (change.after() != null && !change.after().isNull()) {
            indexService.renameBrand(uuid(row, COLUMN_ID), text(row, COLUMN_NAME));
        }
    }

    private void reindexCategory(CatalogChange change, JsonNode row) {
        if (change.after() != null && !change.after().isNull()) {
            indexService.reindexCategory(uuid(row, COLUMN_ID));
        }
    }

    private UUID uuid(JsonNode row, String column) {
        try {
            return UUID.fromString(text(row, column));
        } catch (IllegalArgumentException ex) {
            throw new InvalidChangeEventException("Column " + column + " is not an id: " + row.get(column), ex);
        }
    }

    private String text(JsonNode row, String column) {
        JsonNode value = row.get(column);
        if (value == null || value.isNull()) {
            throw new InvalidChangeEventException("Row has no column " + column, null);
        }
        return value.asText();
    }
}
