package com.ecm.search.messaging;

import com.ecm.search.messaging.kafka.consumer.CatalogChangeConsumer;
import com.ecm.search.service.ProductIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CatalogChangeConsumerTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private final ProductIndexService indexService = mock(ProductIndexService.class);
    private final CatalogChangeConsumer consumer = new CatalogChangeConsumer(indexService, new ObjectMapper());

    private static String event(String table, String before, String after) {
        return "{\"before\":" + before + ",\"after\":" + after + ",\"source\":{\"table\":\"" + table + "\"},\"op\":\"u\"}";
    }

    @Test
    void aChangedProductReindexesThatProduct() {
        consumer.onCatalogChanged(event("products", "null", "{\"id\":\"" + ID + "\",\"status\":\"ACTIVE\"}"));

        verify(indexService).reindexProduct(ID);
    }

    @Test
    void aDeletedProductReindexesItFromTheOldRowSoTheDocumentIsDropped() {
        consumer.onCatalogChanged(event("products", "{\"id\":\"" + ID + "\"}", "null"));

        verify(indexService).reindexProduct(ID);
    }

    @Test
    void aChangedVariantOrImageReindexesTheirProduct() {
        consumer.onCatalogChanged(event("product_variants", "null", "{\"id\":\"" + ID + "\",\"product_id\":\"" + PRODUCT_ID + "\"}"));
        consumer.onCatalogChanged(event("product_images", "null", "{\"id\":\"" + ID + "\",\"product_id\":\"" + PRODUCT_ID + "\"}"));

        org.mockito.Mockito.verify(indexService, org.mockito.Mockito.times(2)).reindexProduct(PRODUCT_ID);
    }

    @Test
    void aRenamedBrandOrCategoryIsRenamedInsideTheIndex() {
        consumer.onCatalogChanged(event("brands", "null", "{\"id\":\"" + ID + "\",\"name\":\"Asus\"}"));
        consumer.onCatalogChanged(event("categories", "null", "{\"id\":\"" + ID + "\",\"name\":\"Laptop\"}"));

        verify(indexService).renameBrand(ID, "Asus");
        verify(indexService).renameCategory(ID, "Laptop");
    }

    @Test
    void aDeletedBrandChangesNothing() {
        consumer.onCatalogChanged(event("brands", "{\"id\":\"" + ID + "\",\"name\":\"Asus\"}", "null"));

        verifyNoInteractions(indexService);
    }

    @Test
    void tombstonesAndTablesOfNoInterestAreIgnored() {
        consumer.onCatalogChanged(null);
        consumer.onCatalogChanged(event("outbox_events", "null", "{\"id\":\"" + ID + "\"}"));
        consumer.onCatalogChanged("{\"before\":null,\"after\":null,\"source\":{\"table\":\"products\"}}");

        verifyNoInteractions(indexService);
    }

    @Test
    void anEventThatCannotBeReadIsReportedSoKafkaRetriesIt() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> consumer.onCatalogChanged("not json"));
        verify(indexService, org.mockito.Mockito.never()).renameBrand(any(), anyString());
    }
}
