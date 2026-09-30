package com.ecm.catalog.mapper;

import com.ecm.catalog.dto.response.BrandResponse;
import com.ecm.catalog.entity.Brand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BrandMapper {

    @Mapping(target = "imageUrl", ignore = true)
    BrandResponse toResponse(Brand brand);

    List<BrandResponse> toResponseList(List<Brand> brands);
}
