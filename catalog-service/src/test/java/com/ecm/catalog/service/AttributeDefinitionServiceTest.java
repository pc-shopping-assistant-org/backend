package com.ecm.catalog.service;

import com.ecm.catalog.dto.request.CreateAttributeDefinitionRequest;
import com.ecm.catalog.dto.request.UpdateAttributeDefinitionRequest;
import com.ecm.catalog.entity.AttributeDataType;
import com.ecm.catalog.entity.AttributeDefinition;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.mapper.AttributeDefinitionMapperImpl;
import com.ecm.catalog.repository.AttributeDefinitionRepository;
import com.ecm.catalog.repository.CategoryAttributeRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttributeDefinitionServiceTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    private AttributeDefinitionRepository attributeRepository;
    private CategoryAttributeRepository categoryAttributeRepository;
    private AttributeDefinitionService service;

    @BeforeEach
    void setUp() {
        attributeRepository = mock(AttributeDefinitionRepository.class);
        categoryAttributeRepository = mock(CategoryAttributeRepository.class);
        service = new AttributeDefinitionService(attributeRepository, categoryAttributeRepository, new AttributeDefinitionMapperImpl());
        when(attributeRepository.save(any(AttributeDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static CreateAttributeDefinitionRequest create(String key, AttributeDataType type, List<String> allowed, Boolean filterable) {
        return new CreateAttributeDefinitionRequest(key, "Display", type, null, allowed, null, filterable, null);
    }

    @Test
    void createStoresEnumWithAllowedValuesAndDefaultsFlagsToFalse() {
        service.create(create("ram_type", AttributeDataType.ENUM, List.of("DDR4", "DDR5"), null));

        ArgumentCaptor<AttributeDefinition> saved = ArgumentCaptor.forClass(AttributeDefinition.class);
        verify(attributeRepository).save(saved.capture());
        assertEquals(List.of("DDR4", "DDR5"), saved.getValue().getAllowedValues());
        assertFalse(saved.getValue().isFilterable());
        assertFalse(saved.getValue().isComparable());
    }

    @Test
    void createKeepsExplicitFlags() {
        service.create(create("capacity_gb", AttributeDataType.NUMBER, null, true));

        ArgumentCaptor<AttributeDefinition> saved = ArgumentCaptor.forClass(AttributeDefinition.class);
        verify(attributeRepository).save(saved.capture());
        assertTrue(saved.getValue().isFilterable());
    }

    @Test
    void createRejectsDuplicateKey() {
        when(attributeRepository.existsByKey("ram_type")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.create(create("ram_type", AttributeDataType.STRING, null, null)));
        verify(attributeRepository, never()).save(any());
    }

    @Test
    void createRejectsEnumWithoutValuesAndNonEnumWithValues() {
        BusinessException enumEx = assertThrows(BusinessException.class,
                () -> service.create(create("a", AttributeDataType.ENUM, List.of(), null)));
        BusinessException numberEx = assertThrows(BusinessException.class,
                () -> service.create(create("b", AttributeDataType.NUMBER, List.of("x"), null)));

        assertEquals(CatalogErrorCode.INVALID_ATTRIBUTE_DEFINITION, enumEx.getErrorCode());
        assertEquals(CatalogErrorCode.INVALID_ATTRIBUTE_DEFINITION, numberEx.getErrorCode());
    }

    @Test
    void updateKeepsKeyAndDataType() {
        AttributeDefinition existing = AttributeDefinition.builder().id(ID).key("ram_type").displayName("Old")
                .dataType(AttributeDataType.ENUM).allowedValues(new java.util.ArrayList<>(List.of("DDR4"))).build();
        when(attributeRepository.findById(ID)).thenReturn(Optional.of(existing));

        service.update(ID, new UpdateAttributeDefinitionRequest("New", "GB", List.of("DDR4", "DDR5"), null, true, true));

        assertEquals("ram_type", existing.getKey());
        assertEquals(AttributeDataType.ENUM, existing.getDataType());
        assertEquals("New", existing.getDisplayName());
        assertEquals(List.of("DDR4", "DDR5"), existing.getAllowedValues());
        assertTrue(existing.isFilterable());
    }

    @Test
    void deleteIsBlockedWhileACategoryUsesTheAttribute() {
        AttributeDefinition existing = AttributeDefinition.builder().id(ID).key("k").dataType(AttributeDataType.STRING).build();
        when(attributeRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(categoryAttributeRepository.existsByAttributeId(ID)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(ID));

        assertEquals(CatalogErrorCode.ATTRIBUTE_IN_USE, ex.getErrorCode());
        verify(attributeRepository, never()).delete(any());
    }

    @Test
    void deleteRemovesUnusedAttribute() {
        AttributeDefinition existing = AttributeDefinition.builder().id(ID).key("k").dataType(AttributeDataType.STRING).build();
        when(attributeRepository.findById(ID)).thenReturn(Optional.of(existing));

        service.delete(ID);

        verify(attributeRepository).delete(existing);
    }
}
