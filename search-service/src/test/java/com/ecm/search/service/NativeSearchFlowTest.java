package com.ecm.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.ecm.search.client.EmbeddingClient;
import com.ecm.search.dto.request.ProductSearchFilterRequest;
import com.ecm.search.entity.ProductDocument;
import com.ecm.search.mapper.ProductDocumentMapperImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Run only against the disposable local Elasticsearch container, never the shared server. */
@EnabledIfSystemProperty(named = "pcshop.search.native", matches = "true")
class NativeSearchFlowTest {
    @Test
    void indexesActualMappingAndSearchesLexicalAndSemanticWithSameVariantFilters() throws Exception {
        var client = ElasticsearchClient.of(config -> config.host("http://127.0.0.1:19201"));
        var operations = new ElasticsearchTemplate(client);
        var redis = new LettuceConnectionFactory("127.0.0.1", 19202);
        redis.afterPropertiesSet();
        redis.start();
        var redisTemplate = new StringRedisTemplate(redis);
        List<String> cacheKeys = new ArrayList<>();
        var indices = operations.indexOps(ProductDocument.class);
        // The named container is dedicated to this test; this index must be absent.
        assertThat(indices.exists()).isFalse();
        try {
            assertThat(indices.createWithMapping()).isTrue();
            List<Float> vector = new ArrayList<>(java.util.Collections.nCopies(EmbeddingClient.EMBEDDING_DIMS, 0f));
            vector.set(0, 1f);
            float[] embedding = new float[EmbeddingClient.EMBEDDING_DIMS];
            embedding[0] = 1f;
            String matching = UUID.randomUUID().toString();
            for (boolean stockAtAffordablePrice : List.of(true, false)) {
                var variants = stockAtAffordablePrice
                        ? List.of(ProductDocument.VariantDocument.builder().price(100L).inStock(true).build())
                        : List.of(ProductDocument.VariantDocument.builder().price(100L).inStock(false).build(),
                                  ProductDocument.VariantDocument.builder().price(1000L).inStock(true).build());
                operations.save(ProductDocument.builder()
                        .id(stockAtAffordablePrice ? matching : UUID.randomUUID().toString())
                        .name("Wireless headset").brandName("Sony").categoryName("Headsets")
                        .minPrice(100L).maxPrice(stockAtAffordablePrice ? 100L : 1000L)
                        .prices(stockAtAffordablePrice ? List.of(100L) : List.of(100L, 1000L))
                        .inStock(true).variants(variants).embedding(embedding).build());
            }
            indices.refresh();
            EmbeddingClient model = mock(EmbeddingClient.class);
            when(model.embed("wireless audio")).thenReturn(vector);
            var cache = new SearchResultCache(redisTemplate, new ObjectMapper(), Duration.ofMinutes(1), 500);
            var service = new ProductSearchService(operations, new ProductDocumentMapperImpl(), model, cache);
            for (String semantic : new String[]{null, "wireless audio", ""}) {
                var filter = new ProductSearchFilterRequest(null, null, "Headsets", "Sony", null, 200L, true, semantic);
                cacheKeys.add(SearchResultCache.buildKey(semantic == null ? "headset" : null, filter));
                var result = service.search(semantic == null ? "headset" : null, filter, 0, 10);
                assertThat(result.getContent()).singleElement().satisfies(hit ->
                        assertThat(hit.id().toString()).isEqualTo(matching));
                assertThat(service.search(semantic == null ? "headset" : null, filter, 0, 1)
                        .getContent()).hasSize(1);
            }
            verify(model, times(1)).embed("wireless audio");
        } finally {
            if (indices.exists()) indices.delete();
            if (!cacheKeys.isEmpty()) redisTemplate.delete(cacheKeys);
            client.close();
            redis.destroy();
        }
    }
}
