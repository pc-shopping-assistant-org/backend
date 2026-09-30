package com.ecm.catalog.mapper;

import com.ecm.catalog.dto.response.ProductImageResponse;
import com.ecm.catalog.entity.ProductImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductImageMapper {

    @Mapping(target = "url", ignore = true)
    ProductImageResponse toResponse(ProductImage image);

    List<ProductImageResponse> toResponseList(List<ProductImage> images);
}
