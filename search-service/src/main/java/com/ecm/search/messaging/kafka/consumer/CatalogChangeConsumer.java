package com.ecm.search.messaging.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code catalog.changed} is fed by Debezium CDC on catalog-service's DB, not by application
 * code (see docs/02-architecture/service-communication.md, use case 10) — no producer exists
 * yet in this project. This is a placeholder until the Elasticsearch index + CDC connector
 * are set up; it only logs so the wiring can be verified end-to-end once they exist.
 */
@Component
@Slf4j
public class CatalogChangeConsumer {

    @KafkaListener(topics = "catalog.changed", groupId = "search-service")
    public void onCatalogChanged(String payload) {
        log.info("Received catalog.changed event (indexing not implemented yet): {}", payload);
    }
}
