package com.ecm.sync.service;

import com.ecm.common.exception.ExternalServiceException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.ApiResponse;
import com.ecm.sync.client.CatalogServiceClient;
import com.ecm.sync.dto.response.CatalogCategoryResponse;
import com.ecm.sync.dto.response.CatalogProductResponse;
import com.ecm.sync.entity.ProductDocument;
import com.ecm.sync.mapper.ProductDocumentMapperImpl;
import com.ecm.sync.repository.ProductSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductIndexServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID BRAND_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    private static final UUID PARENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c4");
    private static final UUID ROOT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c5");

    private final CatalogServiceClient catalogClient = mock(CatalogServiceClient.class);
    private final ProductSearchRepository repository = mock(ProductSearchRepository.class);
    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private ProductIndexService service;

    @BeforeEach
    void setUp() {
        service = new ProductIndexService(catalogClient, repository, operations, new ProductDocumentMapperImpl());
        when(catalogClient.getCategories()).thenReturn(ApiResponse.success(List.of(
                new CatalogCategoryResponse(ROOT_ID, null),
                new CatalogCategoryResponse(PARENT_ID, ROOT_ID),
                new CatalogCategoryResponse(CATEGORY_ID, PARENT_ID))));
    }

    private CatalogProductResponse product(List<CatalogProductResponse.Image> images, List<CatalogProductResponse.Variant> variants) {
        return new CatalogProductResponse(PRODUCT_ID, "RTX 4070", "rtx-4070", BRAND_ID, "Asus", CATEGORY_ID, "Card đồ họa",
                "Fast card", images, variants);
    }

    private ProductDocument savedDocument() {
        ArgumentCaptor<ProductDocument> captor = ArgumentCaptor.forClass(ProductDocument.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void indexesTheProductWithItsPriceRangeStockAndMainImage() {
        when(catalogClient.getProduct(PRODUCT_ID)).thenReturn(ApiResponse.success(product(
                List.of(new CatalogProductResponse.Image("http://img/other.png", false), new CatalogProductResponse.Image("http://img/main.png", true)),
                List.of(new CatalogProductResponse.Variant(9_000_000L, 0), new CatalogProductResponse.Variant(7_000_000L, 3)))));

        service.reindexProduct(PRODUCT_ID);

        ProductDocument document = savedDocument();
        assertThat(document.getId()).isEqualTo(PRODUCT_ID.toString());
        assertThat(document.getName()).isEqualTo("RTX 4070");
        assertThat(document.getBrandId()).isEqualTo(BRAND_ID.toString());
        assertThat(document.getBrandName()).isEqualTo("Asus");
        assertThat(document.getCategoryId()).isEqualTo(CATEGORY_ID.toString());
        assertThat(document.getCategoryName()).isEqualTo("Card đồ họa");
        assertThat(document.getMinPrice()).isEqualTo(7_000_000L);
        assertThat(document.getMaxPrice()).isEqualTo(9_000_000L);
        assertThat(document.getPrices()).containsExactlyInAnyOrder(9_000_000L, 7_000_000L);
        assertThat(document.isInStock()).isTrue();
        assertThat(document.getMainImageUrl()).isEqualTo("http://img/main.png");
        assertThat(document.getIndexedAt()).isNotNull();
    }

    @Test
    void aProductWithoutVariantsOrImagesIsStillIndexedWithoutPrices() {
        when(catalogClient.getProduct(PRODUCT_ID)).thenReturn(ApiResponse.success(product(null, null)));

        service.reindexProduct(PRODUCT_ID);

        ProductDocument document = savedDocument();
        assertThat(document.getMinPrice()).isNull();
        assertThat(document.getMaxPrice()).isNull();
        assertThat(document.isInStock()).isFalse();
        assertThat(document.getMainImageUrl()).isNull();
    }

    @Test
    void withoutAMainImageTheFirstOneIsUsed() {
        when(catalogClient.getProduct(PRODUCT_ID)).thenReturn(ApiResponse.success(product(
                List.of(new CatalogProductResponse.Image("http://img/first.png", false)), List.of())));

        service.reindexProduct(PRODUCT_ID);

        assertThat(savedDocument().getMainImageUrl()).isEqualTo("http://img/first.png");
    }

    @Test
    void aProductTheCatalogNoLongerShowsIsRemovedFromTheIndex() {
        when(catalogClient.getProduct(PRODUCT_ID)).thenThrow(new ResourceNotFoundException("gone"));

        service.reindexProduct(PRODUCT_ID);

        verify(repository).deleteById(PRODUCT_ID.toString());
        verify(repository, never()).save(any());
    }

    @Test
    void aCatalogOutageIsReportedSoTheChangeIsRetried() {
        when(catalogClient.getProduct(PRODUCT_ID)).thenThrow(new ExternalServiceException("catalog-service", "down"));

        assertThatThrownBy(() -> service.reindexProduct(PRODUCT_ID)).isInstanceOf(ExternalServiceException.class);
        verify(repository, never()).deleteById(any(String.class));
    }

    @Test
    void renamingABrandUpdatesEveryProductOfTheBrandInsideTheIndex() {
        when(operations.getIndexCoordinatesFor(ProductDocument.class)).thenReturn(IndexCoordinates.of("products"));

        service.renameBrand(BRAND_ID, "ASUS ROG");

        ArgumentCaptor<UpdateQuery> captor = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(operations).updateByQuery(captor.capture(), any(IndexCoordinates.class));
        assertThat(captor.getValue().getScript()).contains("brandName");
        assertThat(captor.getValue().getParams()).containsEntry("name", "ASUS ROG");
    }

    @Test
    void theDocumentKnowsTheCategoryAndEveryParentAboveIt() {
        when(catalogClient.getProduct(PRODUCT_ID)).thenReturn(ApiResponse.success(product(null, null)));

        service.reindexProduct(PRODUCT_ID);

        assertThat(savedDocument().getCategoryIds()).containsExactly(CATEGORY_ID.toString(), PARENT_ID.toString(), ROOT_ID.toString());
    }

    @Test
    void aLoopInTheCategoryDataEndsTheWalkInsteadOfLoopingForever() {
        when(catalogClient.getCategories()).thenReturn(ApiResponse.success(List.of(
                new CatalogCategoryResponse(CATEGORY_ID, PARENT_ID),
                new CatalogCategoryResponse(PARENT_ID, CATEGORY_ID))));
        when(catalogClient.getProduct(PRODUCT_ID)).thenReturn(ApiResponse.success(product(null, null)));

        service.reindexProduct(PRODUCT_ID);

        assertThat(savedDocument().getCategoryIds()).containsExactly(CATEGORY_ID.toString(), PARENT_ID.toString());
    }

    @Test
    void aCategoryAbsentFromTheListStillKeepsItsOwnId() {
        when(catalogClient.getCategories()).thenReturn(ApiResponse.success(List.of()));
        when(catalogClient.getProduct(PRODUCT_ID)).thenReturn(ApiResponse.success(product(null, null)));

        service.reindexProduct(PRODUCT_ID);

        assertThat(savedDocument().getCategoryIds()).containsExactly(CATEGORY_ID.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aChangedCategoryRebuildsEveryProductBelowIt() {
        UUID other = UUID.fromString("00000000-0000-0000-0000-0000000000c6");
        SearchHits<ProductDocument> hits = mock(SearchHits.class);
        List<SearchHit<ProductDocument>> searchHits = List.of(PRODUCT_ID, other).stream().map(id -> {
            SearchHit<ProductDocument> hit = mock(SearchHit.class);
            when(hit.getContent()).thenReturn(ProductDocument.builder().id(id.toString()).build());
            return hit;
        }).toList();
        when(hits.getSearchHits()).thenReturn(searchHits);
        when(operations.search(any(NativeQuery.class), eq(ProductDocument.class))).thenReturn(hits);
        when(catalogClient.getProduct(any(UUID.class))).thenReturn(ApiResponse.success(product(null, null)));

        service.reindexCategory(PARENT_ID);

        verify(catalogClient).getProduct(PRODUCT_ID);
        verify(catalogClient).getProduct(other);
        verify(repository, times(2)).save(any(ProductDocument.class));
    }
}
