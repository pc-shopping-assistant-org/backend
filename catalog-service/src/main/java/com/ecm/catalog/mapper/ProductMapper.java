package com.ecm.catalog.mapper;

import com.ecm.catalog.dto.response.ProductDetailResponse;
import com.ecm.catalog.dto.response.ProductSummaryResponse;
import com.ecm.catalog.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "brandName", ignore = true)
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "minPrice", ignore = true)
    @Mapping(target = "maxPrice", ignore = true)
    @Mapping(target = "mainImageUrl", ignore = true)
    ProductSummaryResponse toSummaryResponse(Product product);

    @Mapping(target = "brandName", ignore = true)
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "variants", ignore = true)
    ProductDetailResponse toDetailResponse(Product product);

    Product toEntity(com.ecm.catalog.dto.request.CreateProductRequest request);

    List<ProductSummaryResponse> toSummaryResponseList(List<Product> products);
}
