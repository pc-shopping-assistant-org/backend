package com.ecm.catalog.dto.response;

import com.ecm.catalog.entity.AttributeDataType;

import java.util.List;
import java.util.UUID;

/** The specification form of a category: ordered groups of attributes a product in it must fill in. */
public record CategoryAttributeTemplateResponse(UUID categoryId, List<Group> groups) {

    public record Group(UUID id, String name, int displayOrder, List<Item> attributes) {}

    public record Item(
            UUID attributeId,
            String key,
            String displayName,
            AttributeDataType dataType,
            String unit,
            List<String> allowedValues,
            boolean required,
            int displayOrder
    ) {}
}
