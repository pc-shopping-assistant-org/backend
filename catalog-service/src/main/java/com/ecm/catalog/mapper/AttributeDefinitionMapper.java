package com.ecm.catalog.mapper;

import com.ecm.catalog.dto.request.CreateAttributeDefinitionRequest;
import com.ecm.catalog.dto.request.UpdateAttributeDefinitionRequest;
import com.ecm.catalog.dto.response.AttributeDefinitionResponse;
import com.ecm.catalog.entity.AttributeDefinition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AttributeDefinitionMapper {

    AttributeDefinitionResponse toResponse(AttributeDefinition attribute);

    List<AttributeDefinitionResponse> toResponseList(List<AttributeDefinition> attributes);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "filterable", expression = "java(Boolean.TRUE.equals(request.filterable()))")
    @Mapping(target = "comparable", expression = "java(Boolean.TRUE.equals(request.comparable()))")
    AttributeDefinition toEntity(CreateAttributeDefinitionRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "key", ignore = true)
    @Mapping(target = "dataType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "filterable", expression = "java(Boolean.TRUE.equals(request.filterable()))")
    @Mapping(target = "comparable", expression = "java(Boolean.TRUE.equals(request.comparable()))")
    void updateEntity(UpdateAttributeDefinitionRequest request, @MappingTarget AttributeDefinition attribute);
}
