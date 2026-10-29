package com.ecm.sync.service;

import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.sync.client.CatalogServiceClient;
import com.ecm.sync.dto.response.CatalogProductResponse;
import com.ecm.sync.entity.ProductDocument;
import com.ecm.sync.mapper.ProductDocumentMapper;
import com.ecm.sync.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the product index in step with the catalog. A product is indexed from the catalog's own public view of it, so
 * whatever the storefront would not show (inactive, deleted, unknown) is removed from the index.
 */
@Service
@RequiredArgsConstructor
public class ProductIndexService {

    private static final int REINDEX_PAGE_SIZE = 500;

    private final CatalogServiceClient catalogClient;
    private final ProductSearchRepository repository;
    private final ElasticsearchOperations operations;
    private final ProductDocumentMapper mapper;

    /** Rebuilds the document of one product from the catalog, or drops it when the catalog no longer shows the product. */
    public void reindexProduct(UUID productId) {
        // 1. The product as the storefront sees it; a 404 means it is not shown (any more)
        CatalogProductResponse product;
        try {
            product = catalogClient.getProduct(productId).getData();
        } catch (ResourceNotFoundException ex) {
            repository.deleteById(productId.toString());
            return;
        }

        // 2. Flatten it into one document and replace the old one
        List<CatalogProductResponse.Variant> variants = product.variants() == null ? List.of() : product.variants();
        List<Long> prices = variants.stream().map(CatalogProductResponse.Variant::price).filter(Objects::nonNull).toList();
        boolean inStock = variants.stream().anyMatch(variant -> variant.quantity() != null && variant.quantity() > 0);
        repository.save(mapper.toDocument(product,
                prices.stream().min(Long::compare).orElse(null), prices.stream().max(Long::compare).orElse(null),
                inStock, mainImageUrl(product.images()), categoryChain(product.categoryId()), prices, Instant.now()));
    }

    /** A brand rename changes every product of the brand at once, so it is applied inside the index. */
    public void renameBrand(UUID brandId, String name) {
        updateNameWhere(ProductDocument.FIELD_BRAND_ID, brandId, "brandName", name);
    }

    /**
     * A category change can move a whole subtree (a rename, a new parent), so every product whose category chain
     * passes through it is rebuilt. A category without products yet has nothing to rebuild.
     */
    public void reindexCategory(UUID categoryId) {
        for (UUID productId : productIdsInCategory(categoryId)) {
            reindexProduct(productId);
        }
    }

    /** The ids are all read before any product is rebuilt, because a rebuild can take the product out of the match. */
    private List<UUID> productIdsInCategory(UUID categoryId) {
        List<UUID> ids = new ArrayList<>();
        int page = 0;
        int hitCount;
        do {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(match -> match.term(term -> term.field(ProductDocument.FIELD_CATEGORY_IDS).value(categoryId.toString())))
                    .withPageable(PageRequest.of(page++, REINDEX_PAGE_SIZE))
                    .build();
            List<SearchHit<ProductDocument>> hits = operations.search(query, ProductDocument.class).getSearchHits();
            hits.forEach(hit -> ids.add(UUID.fromString(hit.getContent().getId())));
            hitCount = hits.size();
        } while (hitCount == REINDEX_PAGE_SIZE);
        return ids;
    }

    /** The category and each parent above it, nearest first; a cycle in the data stops the walk instead of looping. */
    private List<String> categoryChain(UUID categoryId) {
        if (categoryId == null) {
            return List.of();
        }
        Map<UUID, UUID> parents = new HashMap<>();
        catalogClient.getCategories().getData().forEach(category -> parents.put(category.id(), category.parentId()));
        List<String> chain = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        for (UUID id = categoryId; id != null && visited.add(id); id = parents.get(id)) {
            chain.add(id.toString());
        }
        return chain;
    }

    private void updateNameWhere(String idField, UUID id, String nameField, String name) {
        UpdateQuery update = UpdateQuery.builder(NativeQuery.builder()
                        .withQuery(query -> query.term(term -> term.field(idField).value(id.toString())))
                        .build())
                .withScript("ctx._source." + nameField + " = params.name")
                .withParams(Map.of("name", name))
                .withAbortOnVersionConflict(false)
                .build();
        operations.updateByQuery(update, operations.getIndexCoordinatesFor(ProductDocument.class));
    }

    private String mainImageUrl(Collection<CatalogProductResponse.Image> images) {
        if (images == null) {
            return null;
        }
        return images.stream().filter(CatalogProductResponse.Image::main).findFirst()
                .or(() -> images.stream().findFirst())
                .map(CatalogProductResponse.Image::url)
                .orElse(null);
    }
}
