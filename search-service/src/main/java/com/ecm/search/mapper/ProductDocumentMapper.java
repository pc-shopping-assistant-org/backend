package com.ecm.search.mapper;

import com.ecm.search.dto.response.CatalogProductResponse;
import com.ecm.search.dto.response.ProductSearchResponse;
import com.ecm.search.entity.ProductDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface ProductDocumentMapper {

    @Mapping(target = "id", source = "product.id")
    @Mapping(target = "name", source = "product.name")
    @Mapping(target = "seoName", source = "product.seoName")
    @Mapping(target = "brandId", source = "product.brandId")
    @Mapping(target = "brandName", source = "product.brandName")
    @Mapping(target = "categoryId", source = "product.categoryId")
    @Mapping(target = "categoryName", source = "product.categoryName")
    @Mapping(target = "description", source = "product.description")
    ProductDocument toDocument(CatalogProductResponse product, Long minPrice, Long maxPrice, boolean inStock,
                               String mainImageUrl, Instant indexedAt);

    ProductSearchResponse toResponse(ProductDocument document);
}
