package com.ecm.sync.mapper;

import com.ecm.sync.dto.response.CatalogProductResponse;
import com.ecm.sync.entity.ProductDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.List;

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
                               String mainImageUrl, List<String> categoryIds, List<Long> prices,
                               Instant indexedAt);
}
