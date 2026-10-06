package com.ecm.catalog.mapper;

import com.ecm.catalog.dto.response.ProductVariantResponse;
import com.ecm.catalog.entity.ProductVariant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductVariantMapper {

    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "options", ignore = true)
    ProductVariantResponse toResponse(ProductVariant variant);
}
