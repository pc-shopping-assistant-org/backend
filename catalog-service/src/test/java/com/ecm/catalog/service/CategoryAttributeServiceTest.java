package com.ecm.catalog.service;

import com.ecm.catalog.dto.request.ReplaceCategoryAttributesRequest;
import com.ecm.catalog.dto.request.ReplaceCategoryAttributesRequest.Group;
import com.ecm.catalog.dto.request.ReplaceCategoryAttributesRequest.Item;
import com.ecm.catalog.entity.AttributeDataType;
import com.ecm.catalog.entity.AttributeDefinition;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Category;
import com.ecm.catalog.entity.CategoryAttribute;
import com.ecm.catalog.entity.CategoryAttributeGroup;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.repository.AttributeDefinitionRepository;
import com.ecm.catalog.repository.CategoryAttributeGroupRepository;
import com.ecm.catalog.repository.CategoryAttributeRepository;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryAttributeServiceTest {

    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
    private static final UUID RAM_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e3");
    private static final UUID TYPE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e4");

    private CategoryRepository categoryRepository;
    private CategoryAttributeGroupRepository groupRepository;
    private CategoryAttributeRepository linkRepository;
    private AttributeDefinitionRepository attributeRepository;
    private CategoryAttributeService service;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        groupRepository = mock(CategoryAttributeGroupRepository.class);
        linkRepository = mock(CategoryAttributeRepository.class);
        attributeRepository = mock(AttributeDefinitionRepository.class);
        service = new CategoryAttributeService(categoryRepository, groupRepository, linkRepository, attributeRepository);
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(
                Category.builder().id(CATEGORY_ID).name("RAM").status(CatalogStatus.ACTIVE).build()));
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
    void getTemplateWithoutGroupsIsEmpty() {
        when(groupRepository.findByCategoryIdOrderByDisplayOrderAscNameAsc(CATEGORY_ID)).thenReturn(List.of());

        assertTrue(service.getTemplate(CATEGORY_ID).groups().isEmpty());
    }

    @Test
    void getTemplateJoinsGroupsLinksAndDefinitions() {
        when(groupRepository.findByCategoryIdOrderByDisplayOrderAscNameAsc(CATEGORY_ID)).thenReturn(List.of(
                CategoryAttributeGroup.builder().id(GROUP_ID).categoryId(CATEGORY_ID).name("Memory").displayOrder(0).build()));
        when(linkRepository.findByCategoryGroupIdInOrderByDisplayOrderAsc(List.of(GROUP_ID))).thenReturn(List.of(
                CategoryAttribute.builder().categoryGroupId(GROUP_ID).attributeId(RAM_ID).required(true).displayOrder(0).build()));
        knownAttributes();

        var template = service.getTemplate(CATEGORY_ID);

        assertEquals("Memory", template.groups().get(0).name());
        var item = template.groups().get(0).attributes().get(0);
        assertEquals("capacity_gb", item.key());
        assertTrue(item.required());
    }

    @Test
    void replaceDeletesOldTemplateThenWritesGroupsAndLinksWithPositionAsDefaultOrder() {
        knownAttributes();
        when(groupRepository.save(any(CategoryAttributeGroup.class))).thenAnswer(invocation -> {
            CategoryAttributeGroup group = invocation.getArgument(0);
            group.setId(GROUP_ID);
            return group;
        });
        when(groupRepository.findByCategoryIdOrderByDisplayOrderAscNameAsc(CATEGORY_ID)).thenReturn(List.of());

        service.replaceTemplate(CATEGORY_ID, new ReplaceCategoryAttributesRequest(List.of(
                new Group(" Memory ", null, List.of(new Item(RAM_ID, true, null), new Item(TYPE_ID, null, 5))))));

        InOrder order = inOrder(linkRepository, groupRepository);
        order.verify(linkRepository).deleteByCategoryId(CATEGORY_ID);
        order.verify(groupRepository).deleteByCategoryId(CATEGORY_ID);
        order.verify(groupRepository).save(any(CategoryAttributeGroup.class));
        ArgumentCaptor<CategoryAttribute> links = ArgumentCaptor.forClass(CategoryAttribute.class);
        verify(linkRepository, org.mockito.Mockito.times(2)).save(links.capture());
        assertEquals(GROUP_ID, links.getAllValues().get(0).getCategoryGroupId());
        assertTrue(links.getAllValues().get(0).isRequired());
        assertEquals(0, links.getAllValues().get(0).getDisplayOrder());
        assertEquals(false, links.getAllValues().get(1).isRequired());
        assertEquals(5, links.getAllValues().get(1).getDisplayOrder());
    }

    @Test
    void replaceRejectsAttributeUsedInTwoGroups() {
        knownAttributes();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.replaceTemplate(CATEGORY_ID,
                new ReplaceCategoryAttributesRequest(List.of(
                        new Group("A", null, List.of(new Item(RAM_ID, true, null))),
                        new Group("B", null, List.of(new Item(RAM_ID, false, null)))))));

        assertEquals(CatalogErrorCode.INVALID_CATEGORY_ATTRIBUTES, ex.getErrorCode());
        verify(linkRepository, never()).deleteByCategoryId(any());
    }

    @Test
    void replaceRejectsDuplicateGroupNamesIgnoringCase() {
        knownAttributes();

        assertThrows(BusinessException.class, () -> service.replaceTemplate(CATEGORY_ID,
                new ReplaceCategoryAttributesRequest(List.of(
                        new Group("Memory", null, List.of()),
                        new Group(" memory ", null, List.of())))));
    }

    @Test
    void replaceRejectsUnknownAttribute() {
        knownAttributes();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.replaceTemplate(CATEGORY_ID,
                new ReplaceCategoryAttributesRequest(List.of(
                        new Group("A", null, List.of(new Item(UUID.randomUUID(), true, null)))))));

        assertEquals(CatalogErrorCode.INVALID_CATEGORY_ATTRIBUTES, ex.getErrorCode());
        verify(groupRepository, never()).save(any());
    }

    @Test
    void replaceWithEmptyGroupsClearsTheTemplate() {
        when(groupRepository.findByCategoryIdOrderByDisplayOrderAscNameAsc(CATEGORY_ID)).thenReturn(List.of());

        var template = service.replaceTemplate(CATEGORY_ID, new ReplaceCategoryAttributesRequest(List.of()));

        verify(groupRepository).deleteByCategoryId(CATEGORY_ID);
        assertTrue(template.groups().isEmpty());
    }
}
