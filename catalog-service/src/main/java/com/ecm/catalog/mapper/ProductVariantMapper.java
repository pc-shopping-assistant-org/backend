package com.ecm.catalog.mapper;

import com.ecm.catalog.dto.request.VariationItemRequest;
import com.ecm.catalog.dto.response.ProductVariantResponse;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.util.BlankText;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", imports = BlankText.class)
public interface ProductVariantMapper {

    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "options", ignore = true)
    @Mapping(target = "hasOrders", ignore = true)
    ProductVariantResponse toResponse(ProductVariant variant);

    /** Stock, status and ownership are not part of the item: the variations save sets them by its own rules. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "productId", ignore = true)
    @Mapping(target = "quantity", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "sku", expression = "java(request.sku().trim())")
    @Mapping(target = "barcode", expression = "java(BlankText.trimmedOrNull(request.barcode()))")
    void updateEntity(VariationItemRequest request, @MappingTarget ProductVariant variant);
}
