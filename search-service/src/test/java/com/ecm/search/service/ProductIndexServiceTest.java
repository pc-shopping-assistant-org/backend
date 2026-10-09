package com.ecm.search.service;

import com.ecm.common.exception.ExternalServiceException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.ApiResponse;
import com.ecm.search.client.CatalogServiceClient;
import com.ecm.search.dto.response.CatalogProductResponse;
import com.ecm.search.entity.ProductDocument;
import com.ecm.search.mapper.ProductDocumentMapperImpl;
import com.ecm.search.repository.ProductSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductIndexServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID BRAND_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    private final CatalogServiceClient catalogClient = mock(CatalogServiceClient.class);
    private final ProductSearchRepository repository = mock(ProductSearchRepository.class);
    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private ProductIndexService service;

    @BeforeEach
    void setUp() {
        service = new ProductIndexService(catalogClient, repository, operations, new ProductDocumentMapperImpl());
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
    void renamingACategoryUpdatesTheCategoryNameField() {
        when(operations.getIndexCoordinatesFor(ProductDocument.class)).thenReturn(IndexCoordinates.of("products"));

        service.renameCategory(CATEGORY_ID, "Laptop");

        ArgumentCaptor<UpdateQuery> captor = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(operations).updateByQuery(captor.capture(), any(IndexCoordinates.class));
        assertThat(captor.getValue().getScript()).contains("categoryName");
    }
}
