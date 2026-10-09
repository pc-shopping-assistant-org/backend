package com.ecm.search.messaging.kafka.consumer;

import com.ecm.search.messaging.event.CatalogChange;
import com.ecm.search.service.ProductIndexService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
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

    @KafkaListener(topics = "catalog.changed", groupId = "search-service")
    @SneakyThrows
    public void onCatalogChanged(String payload) {
        // 1. Debezium also sends a null-valued tombstone after a delete; there is nothing in it to act on
        if (payload == null) {
            return;
        }
        CatalogChange change = objectMapper.readValue(payload, CatalogChange.class);
        JsonNode row = change.row();
        if (change.table() == null || row == null || row.isNull()) {
            return;
        }

        // 2. A changed row makes one product document stale, or renames a brand or category inside many
        switch (change.table()) {
            case TABLE_PRODUCTS -> indexService.reindexProduct(uuid(row, COLUMN_ID));
            case TABLE_PRODUCT_VARIANTS, TABLE_PRODUCT_IMAGES -> indexService.reindexProduct(uuid(row, COLUMN_PRODUCT_ID));
            case TABLE_BRANDS -> renameBrand(change, row);
            case TABLE_CATEGORIES -> renameCategory(change, row);
            default -> log.debug("Ignoring a change of table {}", change.table());
        }
    }

    private void renameBrand(CatalogChange change, JsonNode row) {
        if (change.after() != null && !change.after().isNull()) {
            indexService.renameBrand(uuid(row, COLUMN_ID), row.get(COLUMN_NAME).asText());
        }
    }

    private void renameCategory(CatalogChange change, JsonNode row) {
        if (change.after() != null && !change.after().isNull()) {
            indexService.renameCategory(uuid(row, COLUMN_ID), row.get(COLUMN_NAME).asText());
        }
    }

    private UUID uuid(JsonNode row, String column) {
        return UUID.fromString(row.get(column).asText());
    }
}
