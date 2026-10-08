package com.ecm.catalog.service;

import com.ecm.catalog.dto.response.CategoryAttributeTemplateResponse;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Checks the specifications of a product against the attribute template of its category and returns the
 * normalized {@code attribute key -> value} map to store in {@code products.specifications}.
 * Required attributes must be present, every value must match the data type of its attribute, ENUM values must
 * come from the allowed list, and keys the category does not define are rejected.
 */
@Component
@RequiredArgsConstructor
public class ProductSpecificationValidator {

    private static final String ERROR_SEPARATOR = "; ";

    private final CategoryAttributeService categoryAttributeService;

    public Map<String, Object> validate(UUID categoryId, Map<String, Object> specifications) {
        Map<String, Object> input = specifications == null ? Map.of() : specifications;
        List<CategoryAttributeTemplateResponse.Item> template = categoryAttributeService.getTemplate(categoryId).groups().stream()
                .flatMap(group -> group.attributes().stream()).toList();

        List<String> errors = new ArrayList<>();
        Map<String, Object> normalized = new LinkedHashMap<>();

        // 1. Each template attribute: present when required, typed correctly when given
        for (CategoryAttributeTemplateResponse.Item attribute : template) {
            Object value = input.get(attribute.key());
            if (isBlank(value)) {
                if (attribute.required()) {
                    errors.add(attribute.key() + ": is required");
                }
                continue;
            }
            Object cleaned = coerce(attribute, value, errors);
            if (cleaned != null) {
                normalized.put(attribute.key(), cleaned);
            }
        }

        // 2. Keys outside the template are rejected so specifications never drift from the category definition
        Set<String> known = template.stream().map(CategoryAttributeTemplateResponse.Item::key).collect(Collectors.toSet());
        input.keySet().stream().filter(key -> !known.contains(key))
                .forEach(key -> errors.add(key + ": is not an attribute of this category"));

        if (!errors.isEmpty()) {
            throw new BusinessException(CatalogErrorCode.INVALID_SPECIFICATIONS, String.join(ERROR_SEPARATOR, errors));
        }
        return normalized;
    }

    private Object coerce(CategoryAttributeTemplateResponse.Item attribute, Object value, List<String> errors) {
        switch (attribute.dataType()) {
            case NUMBER -> {
                if (value instanceof Number) {
                    return value;
                }
                errors.add(attribute.key() + ": must be a number");
            }
            case BOOLEAN -> {
                if (value instanceof Boolean) {
                    return value;
                }
                errors.add(attribute.key() + ": must be true or false");
            }
            case STRING -> {
                if (value instanceof String text) {
                    return text.trim();
                }
                errors.add(attribute.key() + ": must be a string");
            }
            case ENUM -> {
                if (value instanceof String text && attribute.allowedValues() != null
                        && attribute.allowedValues().contains(text.trim())) {
                    return text.trim();
                }
                errors.add(attribute.key() + ": must be one of " + attribute.allowedValues());
            }
        }
        return null;
    }

    private boolean isBlank(Object value) {
        return value == null || (value instanceof String text && text.isBlank());
    }
}
