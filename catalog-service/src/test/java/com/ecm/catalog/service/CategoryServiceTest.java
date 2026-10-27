package com.ecm.catalog.service;

import com.ecm.catalog.dto.request.CreateCategoryRequest;
import com.ecm.catalog.dto.request.UpdateCategoryDetailsRequest;
import com.ecm.catalog.dto.request.UpdateCategoryRequest;
import com.ecm.catalog.dto.request.ReorderCategoriesRequest;
import com.ecm.catalog.dto.response.CategoryResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.HardwareTypeEntity;
import com.ecm.catalog.entity.Category;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.mapper.CategoryMapper;
import com.ecm.catalog.mapper.CategoryMapperImpl;
import com.ecm.catalog.repository.CategoryProductCount;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.catalog.repository.HardwareTypeRepository;
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
    private HardwareTypeRepository hardwareTypeRepository;
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        hardwareTypeRepository = mock(HardwareTypeRepository.class);
        CategoryMapper mapper = new CategoryMapperImpl();
        categoryService = new CategoryService(categoryRepository, mapper, productRepository, hardwareTypeRepository);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hardwareTypeRepository.findByCodeAndStatus("OTHER", CatalogStatus.ACTIVE))
                .thenReturn(Optional.of(HardwareTypeEntity.builder().code("OTHER").status(CatalogStatus.ACTIVE).build()));
    }

    private static Category category(UUID id, String name, UUID parentId, CatalogStatus status) {
        return Category.builder().id(id).name(name).seoName(name.toLowerCase()).parentId(parentId).status(status).build();
    }

    // ---- UC-ADM-CAT-002 add ----

    @Test
    void categoryResponseExposesStableHardwareTypeWithoutInferringFromName() {
        Category renamedCategory = category(ID, "Processors", null, CatalogStatus.ACTIVE);
        renamedCategory.setHardwareType(HardwareTypeEntity.builder().code("CPU").build());

        var response = new CategoryMapperImpl().toResponse(renamedCategory);

        assertEquals("CPU", response.getHardwareType());
        assertEquals("Processors", response.getName());
    }

    @Test
    void createTrimsInputDerivesSeoNameAndStoresDescription() {
        var response = categoryService.create(new CreateCategoryRequest("  Graphics Card ", null, "  GPUs  ", null));

        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(saved.capture());
        assertEquals("Graphics Card", saved.getValue().getName());
        assertEquals("graphics-card", saved.getValue().getSeoName());
        assertEquals("GPUs", saved.getValue().getDescription());
        assertEquals(CatalogStatus.ACTIVE, saved.getValue().getStatus());
        assertEquals("OTHER", saved.getValue().getHardwareType().getCode());
        assertEquals("graphics-card", response.getSeoName());
    }

    @Test
    void createStoresDisplayOrder() {
        categoryService.create(new CreateCategoryRequest("Graphics Card", null, null, null, 40));

        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(saved.capture());

        assertEquals(40, saved.getValue().getDisplayOrder());
    }

    @Test
    void createResolvesHardwareTypeFromActiveMasterData() {
        HardwareTypeEntity gaming = HardwareTypeEntity.builder().code("PC_GAMING").status(CatalogStatus.ACTIVE).build();
        when(hardwareTypeRepository.findByCodeAndStatus("PC_GAMING", CatalogStatus.ACTIVE)).thenReturn(Optional.of(gaming));

        var response = categoryService.create(new CreateCategoryRequest("Gaming", null, null, null, 10, "PC_GAMING"));

        assertEquals("PC_GAMING", response.getHardwareType());
        assertThrows(BusinessException.class, () -> categoryService.create(
                new CreateCategoryRequest("Invalid", null, null, null, 10, "MISSING")));
    }

    @Test
    void reorderUpdatesOnlyCategoriesWithTheSameParent() {
        Category first = category(ID, "First", null, CatalogStatus.ACTIVE);
        Category second = category(PARENT_ID, "Second", null, CatalogStatus.ACTIVE);
        when(categoryRepository.findAllById(any())).thenReturn(List.of(first, second));

        categoryService.reorder(new ReorderCategoriesRequest(List.of(
                new ReorderCategoriesRequest.Item(ID, 20),
                new ReorderCategoriesRequest.Item(PARENT_ID, 10))));

        assertEquals(20, first.getDisplayOrder());
        assertEquals(10, second.getDisplayOrder());
        verify(categoryRepository).saveAll(any());
    }

    @Test
    void reorderRejectsMixedParentsWithoutChangingOrder() {
        Category root = category(ID, "Root", null, CatalogStatus.ACTIVE);
        Category child = category(CHILD_ID, "Child", ID, CatalogStatus.ACTIVE);
        when(categoryRepository.findAllById(any())).thenReturn(List.of(root, child));

        assertThrows(BusinessException.class, () -> categoryService.reorder(new ReorderCategoriesRequest(List.of(
                new ReorderCategoriesRequest.Item(ID, 20), new ReorderCategoriesRequest.Item(CHILD_ID, 10)))));

        assertEquals(0, root.getDisplayOrder());
        verify(categoryRepository, never()).saveAll(any());
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

    @Test
    void treeSortsRootsAndChildrenByDisplayOrderThenName() {
        Category laterRoot = category(ID, "Z root", null, CatalogStatus.ACTIVE);
        laterRoot.setDisplayOrder(20);
        Category firstRoot = category(PARENT_ID, "A root", null, CatalogStatus.ACTIVE);
        firstRoot.setDisplayOrder(10);
        Category laterChild = category(CHILD_ID, "Z child", PARENT_ID, CatalogStatus.ACTIVE);
        laterChild.setDisplayOrder(20);
        Category firstChild = category(UUID.fromString("00000000-0000-0000-0000-0000000000a4"), "A child", PARENT_ID, CatalogStatus.ACTIVE);
        firstChild.setDisplayOrder(10);
        when(categoryRepository.findByStatus(CatalogStatus.ACTIVE)).thenReturn(List.of(laterChild, laterRoot, firstChild, firstRoot));

        var roots = categoryService.getCategoryTree();

        assertEquals(List.of(PARENT_ID, ID), roots.stream().map(CategoryResponse::getId).toList());
        assertEquals(List.of(firstChild.getId(), CHILD_ID), roots.get(0).getChildren().stream().map(CategoryResponse::getId).toList());
    }

    // ---- product counts on the storefront category tree ----

    @Test
    void treeCountsTheListedProductsOfACategoryAndOfEverythingUnderIt() {
        Category parent = category(PARENT_ID, "Parent", null, CatalogStatus.ACTIVE);
        Category child = category(CHILD_ID, "Child", PARENT_ID, CatalogStatus.ACTIVE);
        Category other = category(ID, "Other", null, CatalogStatus.ACTIVE);
        when(categoryRepository.findByStatus(CatalogStatus.ACTIVE)).thenReturn(List.of(parent, child, other));
        when(productRepository.countListedByCategory(CatalogStatus.ACTIVE, CatalogStatus.ACTIVE))
                .thenReturn(List.of(new CategoryProductCount(PARENT_ID, 2), new CategoryProductCount(CHILD_ID, 3)));

        var roots = categoryService.getCategoryTree();

        var parentNode = roots.stream().filter(node -> node.getId().equals(PARENT_ID)).findFirst().orElseThrow();
        assertEquals(5, parentNode.getProductCount());
        assertEquals(3, parentNode.getChildren().get(0).getProductCount());
        assertEquals(0, roots.stream().filter(node -> node.getId().equals(ID)).findFirst().orElseThrow().getProductCount());
    }

    @Test
    void flatListCarriesTheSameBranchCounts() {
        Category parent = category(PARENT_ID, "Parent", null, CatalogStatus.ACTIVE);
        Category child = category(CHILD_ID, "Child", PARENT_ID, CatalogStatus.ACTIVE);
        when(categoryRepository.findByStatus(CatalogStatus.ACTIVE)).thenReturn(List.of(parent, child));
        when(productRepository.countListedByCategory(CatalogStatus.ACTIVE, CatalogStatus.ACTIVE))
                .thenReturn(List.of(new CategoryProductCount(CHILD_ID, 4)));

        var all = categoryService.getAllCategories();

        assertEquals(4, all.stream().filter(node -> node.getId().equals(PARENT_ID)).findFirst().orElseThrow().getProductCount());
        assertEquals(4, all.stream().filter(node -> node.getId().equals(CHILD_ID)).findFirst().orElseThrow().getProductCount());
    }

    @Test
    void aProductInACategoryThatIsNotActiveCountsForNoCategory() {
        Category parent = category(PARENT_ID, "Parent", null, CatalogStatus.ACTIVE);
        when(categoryRepository.findByStatus(CatalogStatus.ACTIVE)).thenReturn(List.of(parent));
        when(productRepository.countListedByCategory(CatalogStatus.ACTIVE, CatalogStatus.ACTIVE))
                .thenReturn(List.of(new CategoryProductCount(CHILD_ID, 7)));

        var roots = categoryService.getCategoryTree();

        assertEquals(0, roots.get(0).getProductCount());
    }
}
