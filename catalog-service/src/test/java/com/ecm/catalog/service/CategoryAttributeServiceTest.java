package com.ecm.catalog.service;

import com.ecm.catalog.dto.request.ReplaceCategoryAttributesRequest;
import com.ecm.catalog.dto.request.ReplaceCategoryAttributesRequest.Item;
import com.ecm.catalog.entity.AttributeDataType;
import com.ecm.catalog.entity.AttributeDefinition;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Category;
import com.ecm.catalog.entity.CategoryAttribute;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.repository.AttributeDefinitionRepository;
import com.ecm.catalog.repository.CategoryAttributeRepository;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryAttributeServiceTest {

    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID PARENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e0");
    private static final UUID RAM_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e3");
    private static final UUID TYPE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e4");

    private CategoryRepository categoryRepository;
    private CategoryAttributeRepository linkRepository;
    private AttributeDefinitionRepository attributeRepository;
    private CategoryAttributeService service;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        linkRepository = mock(CategoryAttributeRepository.class);
        attributeRepository = mock(AttributeDefinitionRepository.class);
        service = new CategoryAttributeService(categoryRepository, linkRepository, attributeRepository);
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(
                Category.builder().id(CATEGORY_ID).name("RAM").status(CatalogStatus.ACTIVE).build()));
        when(categoryRepository.findSelfAndAncestorIds(CATEGORY_ID)).thenReturn(List.of(CATEGORY_ID));
        when(categoryRepository.findAllById(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<UUID> ids = invocation.getArgument(0);
            return java.util.stream.Stream.of(
                    Category.builder().id(PARENT_ID).name("Memory").status(CatalogStatus.ACTIVE).build(),
                    Category.builder().id(CATEGORY_ID).name("RAM").status(CatalogStatus.ACTIVE).build())
                    .filter(category -> ids.contains(category.getId())).toList();
        });
    }

    private static AttributeDefinition definition(UUID id, String key, AttributeDataType type) {
        return AttributeDefinition.builder().id(id).key(key).displayName(key).dataType(type).build();
    }

    private void knownAttributes() {
        when(attributeRepository.findAllById(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<UUID> ids = invocation.getArgument(0);
            return java.util.stream.Stream.of(definition(RAM_ID, "capacity_gb", AttributeDataType.NUMBER),
                    definition(TYPE_ID, "ram_type", AttributeDataType.STRING)).filter(a -> ids.contains(a.getId())).toList();
        });
    }

    @Test
    void getTemplateOfMissingOrDeletedCategoryIsNotFound() {
        UUID deleted = UUID.randomUUID();
        when(categoryRepository.findById(deleted)).thenReturn(Optional.of(
                Category.builder().id(deleted).name("x").status(CatalogStatus.DELETED).build()));

        assertThrows(ResourceNotFoundException.class, () -> service.getTemplate(deleted));
        assertThrows(ResourceNotFoundException.class, () -> service.getTemplate(UUID.randomUUID()));
    }

    @Test
    void getTemplateWithoutAttributesIsEmpty() {
        when(linkRepository.findByCategoryIdIn(List.of(CATEGORY_ID))).thenReturn(List.of());

        assertTrue(service.getTemplate(CATEGORY_ID).attributes().isEmpty());
    }

    @Test
    void getTemplateJoinsLinksAndDefinitions() {
        when(linkRepository.findByCategoryIdIn(List.of(CATEGORY_ID))).thenReturn(List.of(
                CategoryAttribute.builder().categoryId(CATEGORY_ID).attributeId(RAM_ID).required(true).displayOrder(0).build()));
        knownAttributes();

        var item = service.getTemplate(CATEGORY_ID).attributes().get(0);

        assertEquals("capacity_gb", item.key());
        assertTrue(item.required());
    }

    @Test
    void replaceDeletesOldTemplateThenWritesLinksWithPositionAsDefaultOrder() {
        knownAttributes();
        when(linkRepository.findByCategoryIdIn(List.of(CATEGORY_ID))).thenReturn(List.of());

        service.replaceTemplate(CATEGORY_ID, new ReplaceCategoryAttributesRequest(List.of(
                new Item(RAM_ID, true, null), new Item(TYPE_ID, null, 5))));

        verify(linkRepository).deleteByCategoryId(CATEGORY_ID);
        ArgumentCaptor<CategoryAttribute> links = ArgumentCaptor.forClass(CategoryAttribute.class);
        verify(linkRepository, org.mockito.Mockito.times(2)).save(links.capture());
        assertEquals(CATEGORY_ID, links.getAllValues().get(0).getCategoryId());
        assertTrue(links.getAllValues().get(0).isRequired());
        assertEquals(0, links.getAllValues().get(0).getDisplayOrder());
        assertEquals(false, links.getAllValues().get(1).isRequired());
        assertEquals(5, links.getAllValues().get(1).getDisplayOrder());
    }

    @Test
    void replaceRejectsAttributeListedTwice() {
        knownAttributes();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.replaceTemplate(CATEGORY_ID,
                new ReplaceCategoryAttributesRequest(List.of(new Item(RAM_ID, true, null), new Item(RAM_ID, false, null)))));

        assertEquals(CatalogErrorCode.INVALID_CATEGORY_ATTRIBUTES, ex.getErrorCode());
        verify(linkRepository, never()).deleteByCategoryId(any());
    }

    @Test
    void replaceRejectsUnknownAttribute() {
        knownAttributes();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.replaceTemplate(CATEGORY_ID,
                new ReplaceCategoryAttributesRequest(List.of(new Item(UUID.randomUUID(), true, null)))));

        assertEquals(CatalogErrorCode.INVALID_CATEGORY_ATTRIBUTES, ex.getErrorCode());
        verify(linkRepository, never()).save(any());
    }

    @Test
    void replaceWithNoAttributesClearsTheTemplate() {
        when(linkRepository.findByCategoryIdIn(List.of(CATEGORY_ID))).thenReturn(List.of());

        var template = service.replaceTemplate(CATEGORY_ID, new ReplaceCategoryAttributesRequest(List.of()));

        verify(linkRepository).deleteByCategoryId(CATEGORY_ID);
        assertTrue(template.attributes().isEmpty());
    }

    @Test
    void getTemplateListsInheritedAttributesBeforeTheOwnOnes() {
        when(categoryRepository.findSelfAndAncestorIds(CATEGORY_ID)).thenReturn(List.of(PARENT_ID, CATEGORY_ID));
        when(linkRepository.findByCategoryIdIn(List.of(PARENT_ID, CATEGORY_ID))).thenReturn(List.of(
                CategoryAttribute.builder().categoryId(CATEGORY_ID).attributeId(TYPE_ID).displayOrder(0).build(),
                CategoryAttribute.builder().categoryId(PARENT_ID).attributeId(RAM_ID).required(true).displayOrder(0).build()));
        knownAttributes();

        var items = service.getTemplate(CATEGORY_ID).attributes();

        assertEquals(List.of("capacity_gb", "ram_type"), items.stream().map(item -> item.key()).toList());
        assertTrue(items.get(0).inherited());
        assertEquals(PARENT_ID, items.get(0).sourceCategoryId());
        assertEquals("Memory", items.get(0).sourceCategoryName());
        assertEquals(false, items.get(1).inherited());
        assertEquals(CATEGORY_ID, items.get(1).sourceCategoryId());
    }

    @Test
    void getTemplateDoesNotRepeatAnAttributeAnAncestorAlreadyProvides() {
        when(categoryRepository.findSelfAndAncestorIds(CATEGORY_ID)).thenReturn(List.of(PARENT_ID, CATEGORY_ID));
        when(linkRepository.findByCategoryIdIn(List.of(PARENT_ID, CATEGORY_ID))).thenReturn(List.of(
                CategoryAttribute.builder().categoryId(PARENT_ID).attributeId(RAM_ID).displayOrder(0).build(),
                CategoryAttribute.builder().categoryId(CATEGORY_ID).attributeId(RAM_ID).displayOrder(0).build()));
        knownAttributes();

        var items = service.getTemplate(CATEGORY_ID).attributes();

        assertEquals(1, items.size());
        assertTrue(items.get(0).inherited());
    }

    @Test
    void replaceRejectsAnAttributeInheritedFromAParent() {
        knownAttributes();
        when(categoryRepository.findSelfAndAncestorIds(CATEGORY_ID)).thenReturn(List.of(PARENT_ID, CATEGORY_ID));
        when(linkRepository.findByCategoryIdIn(List.of(PARENT_ID))).thenReturn(List.of(
                CategoryAttribute.builder().categoryId(PARENT_ID).attributeId(RAM_ID).displayOrder(0).build()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.replaceTemplate(CATEGORY_ID,
                new ReplaceCategoryAttributesRequest(List.of(new Item(RAM_ID, true, null)))));

        assertEquals(CatalogErrorCode.INVALID_CATEGORY_ATTRIBUTES, ex.getErrorCode());
        verify(linkRepository, never()).deleteByCategoryId(any());
    }
}
