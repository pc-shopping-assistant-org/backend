package com.ecm.catalog.service;

import com.ecm.catalog.dto.request.ReplaceCategoryAttributesRequest;
import com.ecm.catalog.dto.response.CategoryAttributeTemplateResponse;
import com.ecm.catalog.entity.AttributeDefinition;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.CategoryAttribute;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.repository.AttributeDefinitionRepository;
import com.ecm.catalog.repository.CategoryAttributeRepository;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The attribute template of a category: the ordered attributes a product in that category must describe.
 * Products store the values as a flat {@code attribute key -> value} JSON object in
 * {@code products.specifications}; {@link ProductSpecificationValidator} enforces the template.
 */
@Service
@RequiredArgsConstructor
public class CategoryAttributeService {

    private final CategoryRepository categoryRepository;
    private final CategoryAttributeRepository categoryAttributeRepository;
    private final AttributeDefinitionRepository attributeRepository;

    @Transactional(readOnly = true)
    public CategoryAttributeTemplateResponse getTemplate(UUID categoryId) {
        // 1. The category must exist and not be deleted
        ensureCategoryExists(categoryId);

        // 2. Load the attribute links and their definitions in two queries
        List<CategoryAttribute> links = categoryAttributeRepository.findByCategoryIdOrderByDisplayOrderAsc(categoryId);
        if (links.isEmpty()) {
            return new CategoryAttributeTemplateResponse(categoryId, List.of());
        }
        Set<UUID> attributeIds = links.stream().map(CategoryAttribute::getAttributeId).collect(Collectors.toSet());
        Map<UUID, AttributeDefinition> definitions = attributeRepository.findAllById(attributeIds).stream()
                .collect(Collectors.toMap(AttributeDefinition::getId, Function.identity()));

        // 3. Assemble the response in display order
        return new CategoryAttributeTemplateResponse(categoryId,
                links.stream().map(link -> toItem(link, definitions.get(link.getAttributeId()))).toList());
    }

    @Transactional
    public CategoryAttributeTemplateResponse replaceTemplate(UUID categoryId, ReplaceCategoryAttributesRequest request) {
        // 1. The category must exist and the new template must be internally consistent
        ensureCategoryExists(categoryId);
        validateTemplate(request);

        // 2. Drop the current template, then write the new one
        categoryAttributeRepository.deleteByCategoryId(categoryId);
        for (int index = 0; index < request.attributes().size(); index++) {
            ReplaceCategoryAttributesRequest.Item item = request.attributes().get(index);
            categoryAttributeRepository.save(CategoryAttribute.builder()
                    .categoryId(categoryId).attributeId(item.attributeId()).required(Boolean.TRUE.equals(item.required()))
                    .displayOrder(orderOrIndex(item.displayOrder(), index)).build());
        }

        // 3. Return the stored template
        return getTemplate(categoryId);
    }

    private void validateTemplate(ReplaceCategoryAttributesRequest request) {
        Set<UUID> attributeIds = new HashSet<>();
        for (ReplaceCategoryAttributesRequest.Item item : request.attributes()) {
            // A key appears once per product specification, so an attribute can be listed only once
            if (!attributeIds.add(item.attributeId())) {
                throw new BusinessException(CatalogErrorCode.INVALID_CATEGORY_ATTRIBUTES,
                        "Attribute used more than once: " + item.attributeId());
            }
        }
        if (attributeRepository.findAllById(attributeIds).size() != attributeIds.size()) {
            throw new BusinessException(CatalogErrorCode.INVALID_CATEGORY_ATTRIBUTES, "Unknown attribute in template");
        }
    }

    private void ensureCategoryExists(UUID categoryId) {
        boolean exists = categoryRepository.findById(categoryId)
                .filter(category -> category.getStatus() != CatalogStatus.DELETED).isPresent();
        if (!exists) {
            throw new ResourceNotFoundException("Category", categoryId);
        }
    }

    private int orderOrIndex(Integer displayOrder, int index) {
        return displayOrder != null ? displayOrder : index;
    }

    private CategoryAttributeTemplateResponse.Item toItem(CategoryAttribute link, AttributeDefinition definition) {
        return new CategoryAttributeTemplateResponse.Item(definition.getId(), definition.getKey(), definition.getDisplayName(),
                definition.getDataType(), definition.getUnit(), definition.getAllowedValues(), link.isRequired(), link.getDisplayOrder());
    }
}
