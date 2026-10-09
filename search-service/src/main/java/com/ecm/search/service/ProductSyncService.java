package com.ecm.search.service;

import com.ecm.search.client.CatalogServiceClient;
import com.ecm.search.dto.response.CatalogProductPage;
import com.ecm.search.dto.response.ProductSyncResponse;
import com.ecm.search.entity.ProductDocument;
import com.ecm.search.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Rebuilds the whole product index from the catalog, for the first fill or to repair an index that drifted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSyncService {

    private static final int CATALOG_PAGE_SIZE = 100;

    private final CatalogServiceClient catalogClient;
    private final ProductIndexService indexService;
    private final ProductSearchRepository repository;

    public ProductSyncResponse syncAll() {
        // 1. Every product the catalog lists is indexed again; one failing product does not stop the others
        Set<String> seen = new HashSet<>();
        int indexed = 0;
        int failed = 0;
        UUID cursor = null;
        boolean hasNext = true;
        while (hasNext) {
            CatalogProductPage page = catalogClient.getProducts(CATALOG_PAGE_SIZE, cursor).getData();
            for (CatalogProductPage.Item item : page.items()) {
                seen.add(item.id().toString());
                try {
                    indexService.reindexProduct(item.id());
                    indexed++;
                } catch (RuntimeException ex) {
                    log.warn("Product {} could not be indexed: {}", item.id(), ex.getMessage());
                    failed++;
                }
            }
            hasNext = page.hasNext();
            cursor = hasNext ? UUID.fromString(page.nextCursor()) : null;
        }

        // 2. A document the list did not mention is asked about once more; the catalog's 404 removes it
        int removed = 0;
        for (ProductDocument document : repository.findAll()) {
            if (seen.contains(document.getId())) {
                continue;
            }
            try {
                indexService.reindexProduct(UUID.fromString(document.getId()));
                if (!repository.existsById(document.getId())) {
                    removed++;
                }
            } catch (RuntimeException ex) {
                log.warn("Document {} could not be checked: {}", document.getId(), ex.getMessage());
                failed++;
            }
        }
        return new ProductSyncResponse(indexed, removed, failed);
    }
}
