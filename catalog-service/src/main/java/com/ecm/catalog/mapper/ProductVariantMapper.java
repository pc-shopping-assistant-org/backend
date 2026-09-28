package com.ecm.catalog.mapper;

import com.ecm.catalog.dto.response.ProductVariantResponse;
import com.ecm.catalog.entity.ProductVariant;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductVariantMapper {

    ProductVariantResponse toResponse(ProductVariant variant);
}
