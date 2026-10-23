package com.ecm.catalog.dto.response;

import com.ecm.catalog.entity.AttributeDataType;

import java.util.List;
import java.util.UUID;

/**
 * The specification form of a category: the ordered attributes a product in it must fill in. A category inherits the
 * attributes of its ancestors, listed first (root down); {@code inherited} tells them apart from the ones it owns.
 */
public record CategoryAttributeTemplateResponse(UUID categoryId, List<Item> attributes) {

    public record Item(
            UUID attributeId,
            String key,
            String displayName,
            AttributeDataType dataType,
            String unit,
            List<String> allowedValues,
            boolean required,
            int displayOrder,
            boolean inherited,
            UUID sourceCategoryId,
            String sourceCategoryName
    ) {}
}
