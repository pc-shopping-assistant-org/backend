package com.ecm.catalog.service;

import com.ecm.catalog.dto.request.ReplaceCategoryAttributesRequest;
import com.ecm.catalog.dto.response.CategoryAttributeTemplateResponse;
import com.ecm.catalog.entity.AttributeDefinition;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Category;
import com.ecm.catalog.entity.CategoryAttribute;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.helper.ProductSpecificationValidator;
import com.ecm.catalog.repository.AttributeDefinitionRepository;
import com.ecm.catalog.repository.CategoryAttributeRepository;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The attribute template of a category: the ordered attributes a product in that category must describe, its own
 * after those inherited from its ancestor categories.
 * Products store the values as a flat {@code attribute key -> value} JSON object in
 * {@code products.specifications}; {@link ProductSpecificationValidator} enforces the template.
 */
@Service
@RequiredArgsConstructor
public class CategoryAttributeService {

    private final CategoryRepository categoryRepository;
    private final CategoryAttributeRepository categoryAttributeRepository;
    private final AttributeDefinitionRepository attributeRepository;

    /** The attributes of the category: those inherited from its ancestors (root first), then its own. */
    @Transactional(readOnly = true)
    public CategoryAttributeTemplateResponse getTemplate(UUID categoryId) {
        // 1. The category must exist and not be deleted
        ensureCategoryExists(categoryId);

        // 2. Load the links of the whole chain, the definitions and the category names in three queries
        List<UUID> chain = categoryRepository.findSelfAndAncestorIds(categoryId);
        Map<UUID, List<CategoryAttribute>> linksByCategory = categoryAttributeRepository.findByCategoryIdIn(chain).stream()
                .collect(Collectors.groupingBy(CategoryAttribute::getCategoryId));
        Set<UUID> attributeIds = linksByCategory.values().stream().flatMap(List::stream)
                .map(CategoryAttribute::getAttributeId).collect(Collectors.toSet());
        if (attributeIds.isEmpty()) {
            return new CategoryAttributeTemplateResponse(categoryId, List.of());
        }
        Map<UUID, AttributeDefinition> definitions = attributeRepository.findAllById(attributeIds).stream()
                .collect(Collectors.toMap(AttributeDefinition::getId, Function.identity()));
        Map<UUID, String> names = categoryRepository.findAllById(chain).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        // 3. Assemble in chain order; an attribute an ancestor already provides is not repeated further down
        Set<UUID> seen = new HashSet<>();
        List<CategoryAttributeTemplateResponse.Item> items = new ArrayList<>();
        for (UUID owner : chain) {
            linksByCategory.getOrDefault(owner, List.of()).stream()
                    .sorted(Comparator.comparingInt(CategoryAttribute::getDisplayOrder))
                    .filter(link -> seen.add(link.getAttributeId()))
                    .forEach(link -> items.add(toItem(link, definitions.get(link.getAttributeId()), owner, names.get(owner),
                            !owner.equals(categoryId))));
        }
        return new CategoryAttributeTemplateResponse(categoryId, items);
    }

    /** Replaces the attributes this category owns; what it inherits from its ancestors is edited on them. */
    @Transactional
    public CategoryAttributeTemplateResponse replaceTemplate(UUID categoryId, ReplaceCategoryAttributesRequest request) {
        // 1. The category must exist and the new template must be internally consistent
        ensureCategoryExists(categoryId);
        validateTemplate(categoryId, request);

        // 2. Drop the current template, then write the new one
        categoryAttributeRepository.deleteByCategoryId(categoryId);
        for (int index = 0; index < request.attributes().size(); index++) {
            ReplaceCategoryAttributesRequest.Item item = request.attributes().get(index);
            categoryAttributeRepository.save(CategoryAttribute.builder()
                    .categoryId(categoryId).attributeId(item.attributeId()).required(Boolean.TRUE.equals(item.required()))
                    .displayOrder(Objects.requireNonNullElse(item.displayOrder(), index)).build());
        }

        // 3. Return the stored template
        return getTemplate(categoryId);
    }

    private void validateTemplate(UUID categoryId, ReplaceCategoryAttributesRequest request) {
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
        // An ancestor already provides these to every category below it
        List<UUID> ancestors = categoryRepository.findSelfAndAncestorIds(categoryId).stream()
                .filter(id -> !id.equals(categoryId)).toList();
        if (!ancestors.isEmpty()) {
            categoryAttributeRepository.findByCategoryIdIn(ancestors).stream()
                    .map(CategoryAttribute::getAttributeId).filter(attributeIds::contains).findFirst()
                    .ifPresent(inherited -> {
                        throw new BusinessException(CatalogErrorCode.ATTRIBUTE_ALREADY_INHERITED,
                                "Attribute already inherited from a parent category: " + inherited);
                    });
        }
        // Handing one down that a sub-category already owns would hide its own entry, so it is refused instead
        List<UUID> descendants = categoryRepository.findSelfAndDescendantIds(categoryId).stream()
                .filter(id -> !id.equals(categoryId)).toList();
        if (!descendants.isEmpty()) {
            categoryAttributeRepository.findByCategoryIdIn(descendants).stream()
                    .filter(link -> attributeIds.contains(link.getAttributeId())).findFirst()
                    .ifPresent(link -> {
                        throw new BusinessException(CatalogErrorCode.ATTRIBUTE_USED_BY_SUBCATEGORY,
                                "Attribute " + link.getAttributeId() + " is already used by sub-category " + link.getCategoryId());
                    });
        }
    }

    private void ensureCategoryExists(UUID categoryId) {
        boolean exists = categoryRepository.findById(categoryId)
                .filter(category -> category.getStatus() != CatalogStatus.DELETED).isPresent();
        if (!exists) {
            throw new ResourceNotFoundException("Category", categoryId);
        }
    }

    private CategoryAttributeTemplateResponse.Item toItem(CategoryAttribute link, AttributeDefinition definition, UUID owner,
                                                          String ownerName, boolean inherited) {
        return new CategoryAttributeTemplateResponse.Item(definition.getId(), definition.getKey(), definition.getDisplayName(),
                definition.getDataType(), definition.getUnit(), definition.getAllowedValues(), link.isRequired(), link.getDisplayOrder(),
                inherited, owner, ownerName);
    }
}
