package com.ecm.catalog.util;

import com.ecm.catalog.dto.response.CategoryResponse;
import com.ecm.catalog.repository.CategoryProductCount;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CategoryTrees {

    private CategoryTrees() {
    }

    /** Adds each category's listed products to it and to every category above it, so a parent shows its whole branch. */
    public static void addProductCounts(Map<UUID, CategoryResponse> categories, List<CategoryProductCount> ownCounts) {
        for (CategoryProductCount own : ownCounts) {
            Set<UUID> visited = new HashSet<>();
            CategoryResponse current = categories.get(own.categoryId());
            while (current != null && visited.add(current.getId())) {
                current.setProductCount(current.getProductCount() + own.count());
                current = current.getParentId() == null ? null : categories.get(current.getParentId());
            }
        }
    }

    /**
     * Hangs each category under its parent and returns the ones without a parent; a category whose parent is missing is left out.
     * Roots and every level of children come back by display order, then by name.
     */
    public static List<CategoryResponse> roots(Map<UUID, CategoryResponse> categories) {
        List<CategoryResponse> roots = new ArrayList<>();
        for (CategoryResponse category : categories.values()) {
            if (category.getParentId() == null) {
                roots.add(category);
                continue;
            }
            CategoryResponse parent = categories.get(category.getParentId());
            if (parent != null) {
                parent.getChildren().add(category);
            }
        }
        Comparator<CategoryResponse> storefrontOrder = Comparator.comparingInt(CategoryResponse::getDisplayOrder)
                .thenComparing(CategoryResponse::getName, String.CASE_INSENSITIVE_ORDER);
        roots.sort(storefrontOrder);
        roots.forEach(root -> sortChildren(root, storefrontOrder));
        return roots;
    }

    private static void sortChildren(CategoryResponse category, Comparator<CategoryResponse> order) {
        category.getChildren().sort(order);
        category.getChildren().forEach(child -> sortChildren(child, order));
    }
}
