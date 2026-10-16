package com.ecm.search.mapper;

import com.ecm.search.dto.response.ProductSearchResponse;
import com.ecm.search.entity.ProductDocument;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductDocumentMapper {

    ProductSearchResponse toResponse(ProductDocument document);
}
