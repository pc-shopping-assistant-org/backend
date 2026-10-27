package com.ecm.catalog.bootstrap;

import com.ecm.catalog.dto.response.CategoryAttributeTemplateResponse.Item;
import com.ecm.catalog.entity.AttributeDataType;
import com.ecm.catalog.helper.ProductSpecificationValidator;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JDBC template loading for the offline CLI; validation stays in the canonical catalog validator. */
final class SeedSpecifications {
    private final Map<UUID, UUID> parents = new HashMap<>();
    private final java.util.Set<UUID> retired = new java.util.HashSet<>();
    private final Map<UUID, List<Item>> own = new HashMap<>();

    SeedSpecifications(SeedDatabase catalog) throws Exception {
        for (var row : catalog.query("SELECT id, parent_id, status FROM categories")) {
            parents.put((UUID) row.get("id"), (UUID) row.get("parent_id"));
            if (com.ecm.catalog.entity.CatalogStatus.DELETED.name().equals(row.get("status"))) retired.add((UUID) row.get("id"));
        }
        for (var row : catalog.query("""
                SELECT ca.category_id, ca.required, ca.display_order, d.id, d.key, d.display_name,
                       d.data_type, d.unit, d.allowed_values
                FROM category_attributes ca JOIN attribute_definitions d ON d.id = ca.attribute_id
                ORDER BY ca.display_order
                """)) {
            UUID owner = (UUID) row.get("category_id");
            List<String> allowed = row.get("allowed_values") == null ? null
                    : SeedData.JSON.readValue(row.get("allowed_values").toString(), new TypeReference<>() {});
            own.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(new Item((UUID) row.get("id"),
                    (String) row.get("key"), (String) row.get("display_name"),
                    AttributeDataType.valueOf((String) row.get("data_type")), (String) row.get("unit"),
                    allowed, (Boolean) row.get("required"), (Integer) row.get("display_order"), false, owner, ""));
        }
    }

    Map<String, Object> normalize(UUID categoryId, Map<String, Object> input) {
        var chain = new ArrayList<UUID>();
        for (UUID id = categoryId; id != null; id = parents.get(id)) {
            if (chain.contains(id)) throw new IllegalArgumentException("Cyclic category hierarchy: " + id);
            if (retired.contains(id)) throw new IllegalArgumentException("New seed product references a deleted category: " + id);
            chain.addFirst(id);
        }
        var template = new LinkedHashMap<String, Item>();
        for (UUID id : chain) {
            for (var attribute : own.getOrDefault(id, List.of())) template.putIfAbsent(attribute.key(), attribute);
        }
        var specifications = new LinkedHashMap<>(input);
        specifications.remove("brand");
        return ProductSpecificationValidator.validateTemplate(List.copyOf(template.values()), specifications);
    }
}
