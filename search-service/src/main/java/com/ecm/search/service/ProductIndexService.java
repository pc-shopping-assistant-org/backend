package com.ecm.search.service;

import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.search.client.CatalogServiceClient;
import com.ecm.search.dto.response.CatalogProductResponse;
import com.ecm.search.entity.ProductDocument;
import com.ecm.search.mapper.ProductDocumentMapper;
import com.ecm.search.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Keeps the product index in step with the catalog. A product is indexed from the catalog's own public view of it, so
 * whatever the storefront would not show (inactive, deleted, unknown) is removed from the index.
 */
@Service
@RequiredArgsConstructor
public class ProductIndexService {

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
                inStock, mainImageUrl(product.images()), Instant.now()));
    }

    /** A brand rename changes every product of the brand at once, so it is applied inside the index. */
    public void renameBrand(UUID brandId, String name) {
        updateNameWhere(ProductDocument.FIELD_BRAND_ID, brandId, "brandName", name);
    }

    public void renameCategory(UUID categoryId, String name) {
        updateNameWhere(ProductDocument.FIELD_CATEGORY_ID, categoryId, "categoryName", name);
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
