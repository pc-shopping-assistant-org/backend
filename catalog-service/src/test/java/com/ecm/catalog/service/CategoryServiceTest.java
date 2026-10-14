package com.ecm.catalog.service;

import com.ecm.catalog.dto.request.CreateCategoryRequest;
import com.ecm.catalog.dto.request.UpdateCategoryDetailsRequest;
import com.ecm.catalog.dto.request.UpdateCategoryRequest;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Category;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.mapper.CategoryMapper;
import com.ecm.catalog.mapper.CategoryMapperImpl;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryServiceTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID PARENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID CHILD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        CategoryMapper mapper = new CategoryMapperImpl();
        categoryService = new CategoryService(categoryRepository, mapper, productRepository);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Category category(UUID id, String name, UUID parentId, CatalogStatus status) {
        return Category.builder().id(id).name(name).seoName(name.toLowerCase()).parentId(parentId).status(status).build();
    }

    // ---- UC-ADM-CAT-002 add ----

    @Test
    void createTrimsInputDerivesSeoNameAndStoresDescription() {
        var response = categoryService.create(new CreateCategoryRequest("  Graphics Card ", null, "  GPUs  ", null));

        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(saved.capture());
        assertEquals("Graphics Card", saved.getValue().getName());
        assertEquals("graphics-card", saved.getValue().getSeoName());
        assertEquals("GPUs", saved.getValue().getDescription());
        assertEquals(CatalogStatus.ACTIVE, saved.getValue().getStatus());
        assertEquals("graphics-card", response.getSeoName());
    }

    @Test
    void createRejectsNameHeldByNonDeletedCategory() {
        when(categoryRepository.existsByNameIgnoreCaseAndStatusNot("Graphics Card", CatalogStatus.DELETED)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> categoryService.create(new CreateCategoryRequest("Graphics Card", null, null, null)));

        assertEquals(CatalogErrorCode.RESOURCE_CONFLICT, ex.getErrorCode());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createRejectsInactiveParent() {
        when(categoryRepository.findByIdAndStatus(PARENT_ID, CatalogStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> categoryService.create(new CreateCategoryRequest("Child", null, null, PARENT_ID)));
    }

    // ---- UC-ADM-CAT-003 edit ----

    @Test
    void updateChangesNameAndDescription() {
        Category existing = category(ID, "Old", null, CatalogStatus.ACTIVE);
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(existing));

        categoryService.update(ID, new UpdateCategoryDetailsRequest("New name", null, "new desc", null));

        assertEquals("New name", existing.getName());
        assertEquals("new-name", existing.getSeoName());
        assertEquals("new desc", existing.getDescription());
    }

    @Test
    void updateBlankDescriptionClearsIt() {
        Category existing = category(ID, "Old", null, CatalogStatus.ACTIVE);
        existing.setDescription("keep?");
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(existing));

        categoryService.update(ID, new UpdateCategoryDetailsRequest("Old", null, "   ", null));

        assertNull(existing.getDescription());
    }

    @Test
    void updateKeepingSameNameDoesNotConflictWithItself() {
        Category existing = category(ID, "Same", null, CatalogStatus.ACTIVE);
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameIgnoreCaseAndStatusNot("Same", CatalogStatus.DELETED)).thenReturn(true);

        categoryService.update(ID, new UpdateCategoryDetailsRequest("Same", null, "d", null));

        verify(categoryRepository).save(existing);
    }

    @Test
    void updateRejectsNameOfAnotherCategory() {
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(category(ID, "Old", null, CatalogStatus.ACTIVE)));
        when(categoryRepository.existsByNameIgnoreCaseAndStatusNot("Taken", CatalogStatus.DELETED)).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> categoryService.update(ID, new UpdateCategoryDetailsRequest("Taken", null, null, null)));
    }

    @Test
    void updateRejectsMovingUnderItsOwnDescendant() {
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(category(ID, "Root", null, CatalogStatus.ACTIVE)));
        when(categoryRepository.findByIdAndStatus(CHILD_ID, CatalogStatus.ACTIVE))
                .thenReturn(Optional.of(category(CHILD_ID, "Child", ID, CatalogStatus.ACTIVE)));
        when(categoryRepository.findById(CHILD_ID)).thenReturn(Optional.of(category(CHILD_ID, "Child", ID, CatalogStatus.ACTIVE)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> categoryService.update(ID, new UpdateCategoryDetailsRequest("Root", null, null, CHILD_ID)));

        assertEquals(CatalogErrorCode.INVALID_CATALOG_REFERENCE, ex.getErrorCode());
    }

    @Test
    void updateOfDeletedCategoryIsNotFound() {
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(category(ID, "Gone", null, CatalogStatus.DELETED)));

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.update(ID, new UpdateCategoryDetailsRequest("Gone", null, null, null)));
    }

    @Test
    void patchCannotSoftDeleteBypassingDeleteRules() {
        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.update(ID,
                new UpdateCategoryRequest("Name", null, null, null, CatalogStatus.DELETED)));

        assertEquals(CatalogErrorCode.INVALID_CATALOG_REFERENCE, ex.getErrorCode());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void patchAppliesStatus() {
        Category existing = category(ID, "Name", null, CatalogStatus.ACTIVE);
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(existing));

        categoryService.update(ID, new UpdateCategoryRequest("Name", null, null, null, CatalogStatus.INACTIVE));

        assertEquals(CatalogStatus.INACTIVE, existing.getStatus());
    }

    // ---- UC-ADM-CAT-004 delete ----

    @Test
    void deleteSoftDeletesCategoryWithoutProductsOrChildren() {
        Category existing = category(ID, "Empty", null, CatalogStatus.ACTIVE);
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(existing));

        categoryService.delete(ID);

        assertEquals(CatalogStatus.DELETED, existing.getStatus());
        verify(categoryRepository).save(existing);
    }

    @Test
    void deleteIsBlockedByProducts() {
        Category existing = category(ID, "Busy", null, CatalogStatus.ACTIVE);
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(productRepository.existsByCategoryIdAndStatusNot(ID, CatalogStatus.DELETED)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.delete(ID));

        assertEquals(CatalogErrorCode.CATEGORY_IN_USE, ex.getErrorCode());
        assertEquals(CatalogStatus.ACTIVE, existing.getStatus());
    }

    @Test
    void deleteIsBlockedByLiveChildren() {
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(category(ID, "Parent", null, CatalogStatus.ACTIVE)));
        when(categoryRepository.existsByParentIdAndStatusNot(ID, CatalogStatus.DELETED)).thenReturn(true);

        assertThrows(BusinessException.class, () -> categoryService.delete(ID));
        verify(categoryRepository, never()).save(any());
    }

    // ---- UC-ADM-CAT-001 view ----

    @Test
    void adminListIncludesInactiveButNotDeleted() {
        Category inactive = category(ID, "Hidden", null, CatalogStatus.INACTIVE);
        when(categoryRepository.findByStatusNot(CatalogStatus.DELETED)).thenReturn(List.of(inactive));

        var result = categoryService.getAllCategoriesForAdmin();

        assertEquals(1, result.size());
        assertEquals("INACTIVE", result.get(0).getStatus());
    }

    @Test
    void treeNestsChildrenUnderTheirParent() {
        Category parent = category(PARENT_ID, "Parent", null, CatalogStatus.ACTIVE);
        Category child = category(CHILD_ID, "Child", PARENT_ID, CatalogStatus.ACTIVE);
        when(categoryRepository.findByStatus(CatalogStatus.ACTIVE)).thenReturn(List.of(parent, child));

        var roots = categoryService.getCategoryTree();

        assertEquals(1, roots.size());
        assertEquals(CHILD_ID, roots.get(0).getChildren().get(0).getId());
    }
}
