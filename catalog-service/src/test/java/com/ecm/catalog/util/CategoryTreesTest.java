package com.ecm.catalog.util;

import com.ecm.catalog.dto.response.CategoryResponse;
import com.ecm.catalog.repository.CategoryProductCount;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoryTreesTest {

    private static final UUID ROOT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CHILD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID ORPHAN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    private static CategoryResponse category(UUID id, UUID parentId) {
        CategoryResponse response = new CategoryResponse();
        response.setId(id);
        response.setParentId(parentId);
        return response;
    }

    private static Map<UUID, CategoryResponse> categories(CategoryResponse... all) {
        Map<UUID, CategoryResponse> map = new LinkedHashMap<>();
        for (CategoryResponse category : all) {
            map.put(category.getId(), category);
        }
        return map;
    }

    @Test
    void aChildIsHungUnderItsParentAndOnlyRootsAreReturned() {
        CategoryResponse root = category(ROOT_ID, null);
        CategoryResponse child = category(CHILD_ID, ROOT_ID);

        List<CategoryResponse> roots = CategoryTrees.roots(categories(root, child));

        assertEquals(List.of(root), roots);
        assertEquals(List.of(child), root.getChildren());
    }

    @Test
    void aCategoryWhoseParentIsMissingIsLeftOut() {
        CategoryResponse orphan = category(ORPHAN_ID, UUID.randomUUID());

        assertEquals(List.of(), CategoryTrees.roots(categories(orphan)));
    }

    @Test
    void aParentCountsTheProductsOfItsWholeBranch() {
        CategoryResponse root = category(ROOT_ID, null);
        CategoryResponse child = category(CHILD_ID, ROOT_ID);

        CategoryTrees.addProductCounts(categories(root, child),
                List.of(new CategoryProductCount(ROOT_ID, 2), new CategoryProductCount(CHILD_ID, 3)));

        assertEquals(5, root.getProductCount());
        assertEquals(3, child.getProductCount());
    }

    @Test
    void aParentCycleDoesNotLoopForever() {
        CategoryResponse first = category(ROOT_ID, CHILD_ID);
        CategoryResponse second = category(CHILD_ID, ROOT_ID);

        CategoryTrees.addProductCounts(categories(first, second), List.of(new CategoryProductCount(ROOT_ID, 1)));

        assertEquals(1, first.getProductCount());
        assertEquals(1, second.getProductCount());
    }
}
