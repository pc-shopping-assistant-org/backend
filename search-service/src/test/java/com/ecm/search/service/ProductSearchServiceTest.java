package com.ecm.search.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.response.PageResponse;
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

    @Test
    void returnsTheMatchesInRankOrderWithThePageTotal() {
        UUID id = UUID.randomUUID();
        ProductDocument document = ProductDocument.builder().id(id.toString()).name("RTX 4070").brandName("Asus")
                .minPrice(7_000_000L).inStock(true).mainImageUrl("http://img/a.png").build();
        SearchHits<ProductDocument> result = hits(41, document);
        when(operations.search(any(NativeQuery.class), eq(ProductDocument.class))).thenReturn(result);

        PageResponse<ProductSearchResponse> page = service.search("  rtx 40 ", 1, 20);

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

        assertThat(service.search("zzzz", 0, 20).getContent()).isEmpty();
    }

    @Test
    void searchesTheNameBrandCategoryAndTheHalfTypedName() {
        SearchHits<ProductDocument> result = hits(0);
        when(operations.search(any(NativeQuery.class), eq(ProductDocument.class))).thenReturn(result);

        service.search("rtx 40", 0, 20);

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captor.capture(), eq(ProductDocument.class));
        String query = String.valueOf(captor.getValue().getQuery());
        assertThat(query).contains("name^3").contains("brandName^2").contains("categoryName^1.5").contains("name.prefix").contains("rtx 40");
    }

    @Test
    void rejectsABlankOrOversizedQueryAndABadPage() {
        assertThatThrownBy(() -> service.search(null, 0, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("   ", 0, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("x".repeat(201), 0, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("rtx", -1, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("rtx", 0, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search("rtx", 0, 51)).isInstanceOf(BusinessException.class);
        verify(operations, never()).search(any(NativeQuery.class), eq(ProductDocument.class));
    }
}
