package com.ecm.catalog.service;

import com.ecm.catalog.dto.request.ReplaceCategoryAttributesRequest;
import com.ecm.catalog.dto.response.CategoryAttributeTemplateResponse;
import com.ecm.catalog.entity.AttributeDefinition;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.CategoryAttribute;
import com.ecm.catalog.entity.CategoryAttributeGroup;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.repository.AttributeDefinitionRepository;
import com.ecm.catalog.repository.CategoryAttributeGroupRepository;
import com.ecm.catalog.repository.CategoryAttributeRepository;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The attribute template of a category: the ordered groups of attributes a product in that category must
 * describe. Products store the values as a flat {@code attribute key -> value} JSON object in
 * {@code products.specifications}; {@link ProductSpecificationValidator} enforces the template.
 */
@Service
@RequiredArgsConstructor
public class CategoryAttributeService {

    private final CategoryRepository categoryRepository;
    private final CategoryAttributeGroupRepository groupRepository;
    private final CategoryAttributeRepository categoryAttributeRepository;
    private final AttributeDefinitionRepository attributeRepository;

    @Transactional(readOnly = true)
    public CategoryAttributeTemplateResponse getTemplate(UUID categoryId) {
        // 1. The category must exist and not be deleted
        ensureCategoryExists(categoryId);

        // 2. Load groups, their attribute links and the attribute definitions in three queries
        List<CategoryAttributeGroup> groups = groupRepository.findByCategoryIdOrderByDisplayOrderAscNameAsc(categoryId);
        if (groups.isEmpty()) {
            return new CategoryAttributeTemplateResponse(categoryId, List.of());
        }
        List<UUID> groupIds = groups.stream().map(CategoryAttributeGroup::getId).toList();
        Map<UUID, List<CategoryAttribute>> linksByGroup = categoryAttributeRepository
                .findByCategoryGroupIdInOrderByDisplayOrderAsc(groupIds).stream()
                .collect(Collectors.groupingBy(CategoryAttribute::getCategoryGroupId));
        Set<UUID> attributeIds = linksByGroup.values().stream().flatMap(List::stream)
                .map(CategoryAttribute::getAttributeId).collect(Collectors.toSet());
        Map<UUID, AttributeDefinition> definitions = attributeRepository.findAllById(attributeIds).stream()
                .collect(Collectors.toMap(AttributeDefinition::getId, Function.identity()));

        // 3. Assemble the response in display order
        List<CategoryAttributeTemplateResponse.Group> responseGroups = groups.stream()
                .map(group -> new CategoryAttributeTemplateResponse.Group(group.getId(), group.getName(), group.getDisplayOrder(),
                        linksByGroup.getOrDefault(group.getId(), List.of()).stream()
                                .map(link -> toItem(link, definitions.get(link.getAttributeId()))).toList()))
                .toList();
        return new CategoryAttributeTemplateResponse(categoryId, responseGroups);
    }

    @Transactional
    public CategoryAttributeTemplateResponse replaceTemplate(UUID categoryId, ReplaceCategoryAttributesRequest request) {
        // 1. The category must exist and the new template must be internally consistent
        ensureCategoryExists(categoryId);
        validateTemplate(request);

        // 2. Drop the current template, then write the new one (links first: they reference the groups)
        categoryAttributeRepository.deleteByCategoryId(categoryId);
        groupRepository.deleteByCategoryId(categoryId);
        for (int groupIndex = 0; groupIndex < request.groups().size(); groupIndex++) {
            ReplaceCategoryAttributesRequest.Group group = request.groups().get(groupIndex);
            CategoryAttributeGroup savedGroup = groupRepository.save(CategoryAttributeGroup.builder()
                    .categoryId(categoryId).name(group.name().trim())
                    .displayOrder(orderOrIndex(group.displayOrder(), groupIndex)).build());
            for (int itemIndex = 0; itemIndex < group.attributes().size(); itemIndex++) {
                ReplaceCategoryAttributesRequest.Item item = group.attributes().get(itemIndex);
                categoryAttributeRepository.save(CategoryAttribute.builder()
                        .categoryGroupId(savedGroup.getId()).attributeId(item.attributeId()).required(Boolean.TRUE.equals(item.required()))
                        .displayOrder(orderOrIndex(item.displayOrder(), itemIndex)).build());
            }
        }

        // 3. Return the stored template
        return getTemplate(categoryId);
    }

    private void validateTemplate(ReplaceCategoryAttributesRequest request) {
        Set<String> groupNames = new HashSet<>();
        Set<UUID> attributeIds = new HashSet<>();
        for (ReplaceCategoryAttributesRequest.Group group : request.groups()) {
            if (!groupNames.add(group.name().trim().toLowerCase(Locale.ROOT))) {
                throw new BusinessException(CatalogErrorCode.INVALID_CATEGORY_ATTRIBUTES, "Duplicate group name: " + group.name().trim());
            }
            for (ReplaceCategoryAttributesRequest.Item item : group.attributes()) {
                // A key appears once per product specification, so an attribute can sit in only one group
                if (!attributeIds.add(item.attributeId())) {
                    throw new BusinessException(CatalogErrorCode.INVALID_CATEGORY_ATTRIBUTES,
                            "Attribute used more than once: " + item.attributeId());
                }
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
