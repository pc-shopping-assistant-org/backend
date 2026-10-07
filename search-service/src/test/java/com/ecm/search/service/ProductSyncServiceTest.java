package com.ecm.search.service;

import com.ecm.common.exception.ExternalServiceException;
import com.ecm.common.response.ApiResponse;
import com.ecm.search.client.CatalogServiceClient;
import com.ecm.search.dto.response.CatalogProductPage;
import com.ecm.search.dto.response.ProductSyncResponse;
import com.ecm.search.entity.ProductDocument;
import com.ecm.search.repository.ProductSearchRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSyncServiceTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID STALE = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    private final CatalogServiceClient catalogClient = mock(CatalogServiceClient.class);
    private final ProductIndexService indexService = mock(ProductIndexService.class);
    private final ProductSearchRepository repository = mock(ProductSearchRepository.class);
    private final ProductSyncService service = new ProductSyncService(catalogClient, indexService, repository);

    private static CatalogProductPage page(boolean hasNext, UUID nextCursor, UUID... ids) {
        return new CatalogProductPage(Arrays.stream(ids).map(CatalogProductPage.Item::new).toList(), hasNext,
                nextCursor == null ? null : nextCursor.toString());
    }

    private static ProductDocument document(UUID id) {
        return ProductDocument.builder().id(id.toString()).build();
    }

    @Test
    void everyCatalogPageIsReadAndEveryProductIndexed() {
        when(catalogClient.getProducts(100, null)).thenReturn(ApiResponse.success(page(true, A, A)));
        when(catalogClient.getProducts(100, A)).thenReturn(ApiResponse.success(page(false, null, B)));
        when(repository.findAll()).thenReturn(List.of(document(A), document(B)));

        ProductSyncResponse result = service.syncAll();

        assertThat(result).isEqualTo(new ProductSyncResponse(2, 0, 0));
        verify(indexService).reindexProduct(A);
        verify(indexService).reindexProduct(B);
    }

    @Test
    void aDocumentTheCatalogNoLongerShowsIsCheckedAndCountedAsRemoved() {
        when(catalogClient.getProducts(100, null)).thenReturn(ApiResponse.success(page(false, null, A)));
        when(repository.findAll()).thenReturn(List.of(document(A), document(STALE)));
        when(repository.existsById(STALE.toString())).thenReturn(false);

        ProductSyncResponse result = service.syncAll();

        assertThat(result).isEqualTo(new ProductSyncResponse(1, 1, 0));
        verify(indexService).reindexProduct(STALE);
    }

    @Test
    void aProductThatFailsIsCountedAndTheOthersStillIndexed() {
        when(catalogClient.getProducts(100, null)).thenReturn(ApiResponse.success(page(false, null, A, B)));
        when(repository.findAll()).thenReturn(List.of());
        doThrow(new ExternalServiceException("catalog-service", "down")).when(indexService).reindexProduct(A);

        ProductSyncResponse result = service.syncAll();

        assertThat(result).isEqualTo(new ProductSyncResponse(1, 0, 1));
        verify(indexService).reindexProduct(B);
    }
}
