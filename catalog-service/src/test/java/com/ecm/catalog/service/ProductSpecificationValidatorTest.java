package com.ecm.catalog.service;

import com.ecm.catalog.dto.response.CategoryAttributeTemplateResponse;
import com.ecm.catalog.dto.response.CategoryAttributeTemplateResponse.Group;
import com.ecm.catalog.dto.response.CategoryAttributeTemplateResponse.Item;
import com.ecm.catalog.entity.AttributeDataType;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductSpecificationValidatorTest {

    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private ProductSpecificationValidator validator;

    @BeforeEach
    void setUp() {
        CategoryAttributeService templates = mock(CategoryAttributeService.class);
        List<Item> items = List.of(
                item("capacity_gb", AttributeDataType.NUMBER, null, true, 0),
                item("ram_type", AttributeDataType.ENUM, List.of("DDR4", "DDR5"), true, 1),
                item("brand_line", AttributeDataType.STRING, null, false, 2),
                item("rgb", AttributeDataType.BOOLEAN, null, false, 3));
        when(templates.getTemplate(CATEGORY_ID)).thenReturn(new CategoryAttributeTemplateResponse(CATEGORY_ID,
                List.of(new Group(UUID.randomUUID(), "Memory", 0, items))));
        validator = new ProductSpecificationValidator(templates);
    }

    private static Item item(String key, AttributeDataType type, List<String> allowed, boolean required, int order) {
        return new Item(UUID.randomUUID(), key, key, type, null, allowed, required, order);
    }

    private static Map<String, Object> specs(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private String errorOf(Map<String, Object> input) {
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(CATEGORY_ID, input));
        assertEquals(CatalogErrorCode.INVALID_SPECIFICATIONS, ex.getErrorCode());
        return ex.getMessage();
    }

    @Test
    void acceptsCompleteSpecificationsAndKeepsTemplateOrder() {
        Map<String, Object> result = validator.validate(CATEGORY_ID,
                specs("rgb", true, "ram_type", " DDR5 ", "capacity_gb", 16, "brand_line", " Vengeance "));

        assertEquals(List.of("capacity_gb", "ram_type", "brand_line", "rgb"), List.copyOf(result.keySet()));
        assertEquals("DDR5", result.get("ram_type"));
        assertEquals("Vengeance", result.get("brand_line"));
    }

    @Test
    void optionalAttributesMayBeOmittedOrBlank() {
        Map<String, Object> result = validator.validate(CATEGORY_ID,
                specs("capacity_gb", 8.5, "ram_type", "DDR4", "brand_line", "  ", "rgb", null));

        assertEquals(Map.of("capacity_gb", 8.5, "ram_type", "DDR4"), result);
    }

    @Test
    void rejectsMissingRequiredAttributes() {
        String message = errorOf(specs("brand_line", "x"));

        assertTrue(message.contains("capacity_gb: is required"));
        assertTrue(message.contains("ram_type: is required"));
    }

    @Test
    void nullSpecificationsCountAsEmpty() {
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(CATEGORY_ID, null));

        assertTrue(ex.getMessage().contains("capacity_gb: is required"));
    }

    @Test
    void rejectsWrongTypes() {
        String message = errorOf(specs("capacity_gb", "sixteen", "ram_type", "DDR4", "rgb", "yes", "brand_line", 5));

        assertTrue(message.contains("capacity_gb: must be a number"));
        assertTrue(message.contains("rgb: must be true or false"));
        assertTrue(message.contains("brand_line: must be a string"));
    }

    @Test
    void rejectsEnumValueOutsideAllowedList() {
        assertTrue(errorOf(specs("capacity_gb", 8, "ram_type", "DDR3")).contains("ram_type: must be one of [DDR4, DDR5]"));
    }

    @Test
    void rejectsKeysTheCategoryDoesNotDefine() {
        assertTrue(errorOf(specs("capacity_gb", 8, "ram_type", "DDR4", "voltage", 1.2))
                .contains("voltage: is not an attribute of this category"));
    }

    @Test
    void categoryWithoutTemplateAcceptsOnlyEmptySpecifications() {
        CategoryAttributeService templates = mock(CategoryAttributeService.class);
        UUID bare = UUID.randomUUID();
        when(templates.getTemplate(bare)).thenReturn(new CategoryAttributeTemplateResponse(bare, List.of()));
        ProductSpecificationValidator bareValidator = new ProductSpecificationValidator(templates);

        assertEquals(Map.of(), bareValidator.validate(bare, null));
        assertThrows(BusinessException.class, () -> bareValidator.validate(bare, specs("anything", 1)));
    }
}
