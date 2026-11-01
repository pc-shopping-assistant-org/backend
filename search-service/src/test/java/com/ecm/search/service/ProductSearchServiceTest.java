package com.ecm.search.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.response.PageResponse;
import com.ecm.search.client.EmbeddingClient;
import com.ecm.search.dto.request.ProductSearchFilterRequest;
import com.ecm.search.dto.response.ProductSearchResponse;
import com.ecm.search.entity.ProductDocument;
import com.ecm.search.mapper.ProductDocumentMapperImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSearchServiceTest {

    private static final ProductSearchFilterRequest NO_FILTER = new ProductSearchFilterRequest(null, null, null, null);

    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private final ProductSearchService service = new ProductSearchService(operations, new ProductDocumentMapperImpl());

    @SuppressWarnings("unchecked")
    private SearchHits<ProductDocument> hits(long total, ProductDocument... documents) {
        SearchHits<ProductDocument> hits = mock(SearchHits.class);
        List<SearchHit<ProductDocument>> searchHits = java.util.Arrays.stream(documents).map(document -> {
            SearchHit<ProductDocument> hit = mock(SearchHit.class);
            when(hit.getContent()).thenReturn(document);
            return hit;
        }).toList();
        when(hits.getSearchHits()).thenReturn(searchHits);
        when(hits.getTotalHits()).thenReturn(total);
        return hits;
    }

    /** Runs a search that finds nothing and returns the query that was sent to the index. */
    private String queryFor(ProductSearchFilterRequest filter) {
        SearchHits<ProductDocument> result = hits(0);
        when(operations.search(any(NativeQuery.class), eq(ProductDocument.class))).thenReturn(result);

        service.search("ram", filter, 0, 20);

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captor.capture(), eq(ProductDocument.class));
        return String.valueOf(captor.getValue().getQuery());
    }

    @Test
    void returnsTheMatchesInRankOrderWithThePageTotal() {
        UUID id = UUID.randomUUID();
        ProductDocument document = ProductDocument.builder().id(id.toString()).name("RTX 4070").brandName("Asus")
                .minPrice(7_000_000L).inStock(true).mainImageUrl("http://img/a.png").build();
        SearchHits<ProductDocument> result = hits(41, document);
        when(operations.search(any(NativeQuery.class), eq(ProductDocument.class))).thenReturn(result);

        PageResponse<ProductSearchResponse> page = service.search("  rtx 40 ", NO_FILTER, 1, 20);

        assertThat(page.getContent()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(id);
            assertThat(item.name()).isEqualTo("RTX 4070");
            assertThat(item.minPrice()).isEqualTo(7_000_000L);
            assertThat(item.inStock()).isTrue();
        });
        assertThat(page.getTotalElements()).isEqualTo(41);
        assertThat(page.getPage()).isEqualTo(1);
    }

    @Test
    void noMatchIsAnEmptyPage() {
        SearchHits<ProductDocument> result = hits(0);
        when(operations.search(any(NativeQuery.class), eq(ProductDocument.class))).thenReturn(result);

        assertThat(service.search("zzzz", NO_FILTER, 0, 20).getContent()).isEmpty();
    }

    @Test
    void filterOnlySearchDoesNotRequireAnInventedKeyword() {
        var empty = hits(0);
        when(operations.search(any(NativeQuery.class), eq(ProductDocument.class))).thenReturn(empty);
        var filter = new ProductSearchFilterRequest(null, null, "Headsets", "Sony", null, 200L, true, null);
        assertThat(service.search(null, filter, 0, 5).getContent()).isEmpty();
    }

    @Test
    void searchesTheNameBrandCategoryAndTheHalfTypedName() {
        SearchHits<ProductDocument> result = hits(0);
        when(operations.search(any(NativeQuery.class), eq(ProductDocument.class))).thenReturn(result);

        service.search("rtx 40", NO_FILTER, 0, 20);

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captor.capture(), eq(ProductDocument.class));
        String query = String.valueOf(captor.getValue().getQuery());
        assertThat(query).contains("name^3").contains("brandName^2").contains("categoryName^1.5").contains("name.prefix").contains("rtx 40");
    }

    @Test
    void withoutAFilterTheSearchIsNotNarrowed() {
        assertThat(queryFor(NO_FILTER)).doesNotContain("categoryIds").doesNotContain("brandId").doesNotContain("prices");
    }

    @Test
    void aCategoryMatchesItsSubCategoriesAndABrandItsOwnProducts() {
        UUID category = UUID.randomUUID();
        UUID brand = UUID.randomUUID();

        String query = queryFor(new ProductSearchFilterRequest(category, brand, null, null));

        assertThat(query).contains("categoryIds").contains(category.toString()).contains("brandId").contains(brand.toString());
    }

    @Test
    void aPriceRangeIsAskedOfTheVariantPrices() {
        String query = queryFor(new ProductSearchFilterRequest(null, null, 1_000_000L, 5_000_000L));

        assertThat(query).contains("prices").contains("1000000").contains("5000000");
    }

    @Test
    void anOpenEndedPriceRangeBoundsOneSideOnly() {
        String query = queryFor(new ProductSearchFilterRequest(null, null, 2_000_000L, null));

        assertThat(query).contains("prices").contains("2000000");
    }

    @Test
    void rejectsABlankOrOversizedQueryAndABadPage() {
        assertThatThrownBy(() -> service.search(null, NO_FILTER, 0, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("   ", NO_FILTER, 0, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("x".repeat(201), NO_FILTER, 0, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("rtx", NO_FILTER, -1, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("rtx", NO_FILTER, 0, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("rtx", NO_FILTER, 0, 51)).isInstanceOf(BusinessException.class);
        verify(operations, never()).search(any(NativeQuery.class), eq(ProductDocument.class));
    }

    @Test
    void supportsSemanticNeedsLongerThanAKeywordAndRejectsOverContractLimit() {
        var empty = hits(0);
        when(operations.search(any(NativeQuery.class), eq(ProductDocument.class))).thenReturn(empty);
        var supported = new ProductSearchFilterRequest(null, null, null, null, null, null, null, "x".repeat(1000));
        service.search(null, supported, 0, 10);
        var oversized = new ProductSearchFilterRequest(null, null, null, null, null, null, null, "x".repeat(1001));
        assertThatThrownBy(() -> service.search(null, oversized, 0, 10)).isInstanceOf(BusinessException.class);
        verify(operations, times(1)).search(any(NativeQuery.class), eq(ProductDocument.class));
    }

    @Test
    void rejectsANegativePriceOrARangeThatEndsBeforeItStarts() {
        assertThatThrownBy(() -> service.search("ram", new ProductSearchFilterRequest(null, null, -1L, null), 0, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("ram", new ProductSearchFilterRequest(null, null, null, -5L), 0, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("ram", new ProductSearchFilterRequest(null, null, 9L, 3L), 0, 20)).isInstanceOf(BusinessException.class);
        verify(operations, never()).search(any(NativeQuery.class), eq(ProductDocument.class));
    }

    @Test
    void buildsKnnSearchWithPreFiltersWhenSemanticQueryAndEmbeddingArePresent() {
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        List<Float> vector = Collections.nCopies(EmbeddingClient.EMBEDDING_DIMS, 0.1f);
        when(embeddingClient.embed("tai nghe chống ồn làm việc")).thenReturn(vector);
        SearchHits<ProductDocument> emptyHits = hits(0);
        when(operations.search(any(NativeQuery.class), eq(ProductDocument.class))).thenReturn(emptyHits);

        ProductSearchService semanticService = new ProductSearchService(
                operations, new ProductDocumentMapperImpl(), embeddingClient, null);
        ProductSearchFilterRequest filter = new ProductSearchFilterRequest(
                null, null, "Tai nghe", "Sony", 1_000_000L, 3_000_000L, true, "tai nghe chống ồn làm việc");

        semanticService.search(null, filter, 0, 10);

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captor.capture(), eq(ProductDocument.class));
        NativeQuery built = captor.getValue();
        assertThat(built.getQuery()).as("Semantic filters must stay inside kNN, not OR-match the entire category").isNull();
        assertThat(built.getKnnSearches()).singleElement().satisfies(knn -> {
            assertThat(knn.field()).isEqualTo("embedding");
            assertThat(knn.queryVector()).hasSize(EmbeddingClient.EMBEDDING_DIMS);
            assertThat(knn.filter()).isNotEmpty();
        });
        String filterText = String.valueOf(built.getKnnSearches().getFirst().filter());
        assertThat(filterText)
                .contains("categoryName").contains("Tai nghe")
                .contains("brandName").contains("Sony")
                .contains("variants.price").contains("variants.inStock");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cachesTop500WindowInRedisAndSlicesPagesInMemoryWithoutRequeryingElasticsearch() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        Map<String, String> redisStore = new HashMap<>();
        when(valueOps.get(anyString())).thenAnswer(inv -> redisStore.get(inv.getArgument(0)));
        doAnswer(inv -> {
            redisStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));

        SearchResultCache searchCache = new SearchResultCache(redisTemplate, new ObjectMapper(), Duration.ofMinutes(5), 500);
        ProductSearchService cachedService = new ProductSearchService(
                operations, new ProductDocumentMapperImpl(), null, searchCache);

        ProductDocument[] thirtyFiveDocs = IntStream.range(0, 35)
                .mapToObj(i -> ProductDocument.builder()
                        .id(UUID.randomUUID().toString())
                        .name("Headset #" + i)
                        .brandName("Razer")
                        .minPrice(1_500_000L)
                        .inStock(true)
                        .build())
                .toArray(ProductDocument[]::new);

        SearchHits<ProductDocument> thirtyFiveHits = hits(35, thirtyFiveDocs);
        when(operations.search(any(NativeQuery.class), eq(ProductDocument.class))).thenReturn(thirtyFiveHits);

        // Page 0 (items 0..19): Cache Miss -> queries ES for window of 500, caches in Redis, returns 20 items
        PageResponse<ProductSearchResponse> page0 = cachedService.search("headset", NO_FILTER, 0, 20);
        assertThat(page0.getContent()).hasSize(20);
        assertThat(page0.getContent().getFirst().name()).isEqualTo("Headset #0");
        assertThat(page0.getTotalElements()).isEqualTo(35);

        // Page 1 (items 20..34): Cache Hit -> slices from Redis without calling Elasticsearch again
        PageResponse<ProductSearchResponse> page1 = cachedService.search("headset", NO_FILTER, 1, 20);
        assertThat(page1.getContent()).hasSize(15);
        assertThat(page1.getContent().getFirst().name()).isEqualTo("Headset #20");
        assertThat(page1.getTotalElements()).isEqualTo(35);

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations, times(1)).search(captor.capture(), eq(ProductDocument.class));
        assertThat(captor.getValue().getPageable().getPageSize()).isEqualTo(500);
    }

    @Test
    @SuppressWarnings("unchecked")
    void failsOpenAndQueriesElasticsearchWhenRedisIsDown() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        SearchResultCache searchCache = new SearchResultCache(redisTemplate, new ObjectMapper(), Duration.ofMinutes(5), 500);
        ProductSearchService cachedService = new ProductSearchService(
                operations, new ProductDocumentMapperImpl(), null, searchCache);

        UUID id = UUID.randomUUID();
        ProductDocument doc = ProductDocument.builder().id(id.toString()).name("RAM DDR5").minPrice(2_000_000L).inStock(true).build();
        SearchHits<ProductDocument> oneHit = hits(1, doc);
        when(operations.search(any(NativeQuery.class), eq(ProductDocument.class))).thenReturn(oneHit);

        PageResponse<ProductSearchResponse> page = cachedService.search("ddr5", NO_FILTER, 0, 20);
        assertThat(page.getContent()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(id));
    }
}
