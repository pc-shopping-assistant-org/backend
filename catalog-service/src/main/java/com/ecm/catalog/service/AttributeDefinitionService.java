package com.ecm.catalog.service;

import com.ecm.catalog.dto.request.CreateAttributeDefinitionRequest;
import com.ecm.catalog.dto.request.UpdateAttributeDefinitionRequest;
import com.ecm.catalog.dto.response.AttributeDefinitionResponse;
import com.ecm.catalog.entity.AttributeDataType;
import com.ecm.catalog.entity.AttributeDefinition;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.mapper.AttributeDefinitionMapper;
import com.ecm.catalog.repository.AttributeDefinitionRepository;
import com.ecm.catalog.repository.CategoryAttributeRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.DuplicateResourceException;
import com.ecm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttributeDefinitionService {

    private final AttributeDefinitionRepository attributeRepository;
    private final CategoryAttributeRepository categoryAttributeRepository;
    private final AttributeDefinitionMapper attributeMapper;

    @Transactional
    public AttributeDefinitionResponse create(CreateAttributeDefinitionRequest request) {
        // 1. The key identifies the attribute inside product specifications, so it must be unique
        if (attributeRepository.existsByKey(request.key())) {
            throw new DuplicateResourceException(CatalogErrorCode.RESOURCE_CONFLICT, "AttributeDefinition", "key", request.key());
        }

        // 2. An ENUM needs its allowed values; other types must not carry any
        validateAllowedValues(request.dataType(), request.allowedValues());

        // 3. Persist
        AttributeDefinition attribute = attributeMapper.toEntity(request);
        return attributeMapper.toResponse(attributeRepository.save(attribute));
    }

    @Transactional
    public AttributeDefinitionResponse update(UUID id, UpdateAttributeDefinitionRequest request) {
        // 1. Load the attribute and check the new allowed values against its (immutable) data type
        AttributeDefinition attribute = findAttribute(id);
        validateAllowedValues(attribute.getDataType(), request.allowedValues());

        // 2. Apply the edit
        attributeMapper.updateEntity(request, attribute);
        return attributeMapper.toResponse(attributeRepository.save(attribute));
    }

    @Transactional
    public void delete(UUID id) {
        // 1. An attribute placed in a category template cannot be removed
        AttributeDefinition attribute = findAttribute(id);
        if (categoryAttributeRepository.existsByAttributeId(id)) {
            throw new BusinessException(CatalogErrorCode.ATTRIBUTE_IN_USE);
        }

        // 2. Nothing references it, remove outright
        attributeRepository.delete(attribute);
    }

    @Transactional(readOnly = true)
    public List<AttributeDefinitionResponse> getAll() {
        return attributeMapper.toResponseList(attributeRepository.findAllByOrderByDisplayNameAsc());
    }

    @Transactional(readOnly = true)
    public AttributeDefinitionResponse getById(UUID id) {
        return attributeMapper.toResponse(findAttribute(id));
    }

    private AttributeDefinition findAttribute(UUID id) {
        return attributeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("AttributeDefinition", id));
    }

    private void validateAllowedValues(AttributeDataType dataType, List<String> allowedValues) {
        boolean hasValues = allowedValues != null && !allowedValues.isEmpty();
        if (dataType == AttributeDataType.ENUM && !hasValues) {
            throw new BusinessException(CatalogErrorCode.INVALID_ATTRIBUTE_DEFINITION, "ENUM attribute requires allowedValues");
        }
        if (dataType != AttributeDataType.ENUM && hasValues) {
            throw new BusinessException(CatalogErrorCode.INVALID_ATTRIBUTE_DEFINITION, "allowedValues is only valid for ENUM attributes");
        }
    }
}
