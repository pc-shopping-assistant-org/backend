package com.ecm.catalog.mapper;

import com.ecm.catalog.dto.response.CategoryResponse;
import com.ecm.catalog.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(target = "children", ignore = true)
    CategoryResponse toResponse(Category category);

    Category toEntity(com.ecm.catalog.dto.request.CreateCategoryRequest request);

    List<CategoryResponse> toResponseList(List<Category> categories);
}
