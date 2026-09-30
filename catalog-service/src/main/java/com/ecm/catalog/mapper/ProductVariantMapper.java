package com.ecm.catalog.mapper;

import com.ecm.catalog.dto.response.ProductVariantResponse;
import com.ecm.catalog.entity.ProductVariant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductVariantMapper {

    @Mapping(target = "images", ignore = true)
    @Mapping(target = "options", ignore = true)
    ProductVariantResponse toResponse(ProductVariant variant);

    List<ProductVariantResponse> toResponseList(List<ProductVariant> variants);
}
