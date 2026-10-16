package com.ecm.search.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.response.PageResponse;
import com.ecm.search.dto.request.ProductSearchFilterRequest;
import com.ecm.search.dto.response.ProductSearchResponse;
import com.ecm.search.entity.ProductDocument;
import com.ecm.search.mapper.ProductDocumentMapperImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void rejectsANegativePriceOrARangeThatEndsBeforeItStarts() {
        assertThatThrownBy(() -> service.search("ram", new ProductSearchFilterRequest(null, null, -1L, null), 0, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("ram", new ProductSearchFilterRequest(null, null, null, -5L), 0, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("ram", new ProductSearchFilterRequest(null, null, 9L, 3L), 0, 20)).isInstanceOf(BusinessException.class);
        verify(operations, never()).search(any(NativeQuery.class), eq(ProductDocument.class));
    }
}
