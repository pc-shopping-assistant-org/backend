package com.ecm.catalog.service;

import com.ecm.catalog.client.OrderServiceClient;
import com.ecm.catalog.dto.request.SaveVariationsRequest;
import com.ecm.catalog.dto.request.VariantOptionRequest;
import com.ecm.catalog.dto.request.VariationItemRequest;
import com.ecm.catalog.dto.response.ProductDetailResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Option;
import com.ecm.catalog.entity.Product;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.entity.VariantOption;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.helper.MediaFileResolver;
import com.ecm.catalog.helper.OrderHistoryLookup;
import com.ecm.catalog.helper.ProductAssembler;
import com.ecm.catalog.helper.VariantOptionStore;
import com.ecm.catalog.mapper.ProductVariantMapperImpl;
import com.ecm.catalog.repository.OptionRepository;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.catalog.repository.VariantOptionRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductVariationServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID DEFAULT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    private static final UUID WHITE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d3");
    private static final UUID EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d4");

    private ProductVariantRepository variantRepository;
    private OptionRepository optionRepository;
    private VariantOptionRepository variantOptionRepository;
    private OrderServiceClient orderServiceClient;
    private ProductVariationService service;

    private final List<ProductVariant> variants = new ArrayList<>();
    private final Map<UUID, List<Option>> storedOptions = new HashMap<>();

    @BeforeEach
    void setUp() {
        ProductRepository productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        optionRepository = mock(OptionRepository.class);
        variantOptionRepository = mock(VariantOptionRepository.class);
        orderServiceClient = mock(OrderServiceClient.class);
        ProductAssembler assembler = mock(ProductAssembler.class);
        service = new ProductVariationService(productRepository, variantRepository,
                new VariantOptionStore(optionRepository, variantOptionRepository, variantRepository),
                new OrderHistoryLookup(orderServiceClient), mock(MediaFileResolver.class), assembler, new ProductVariantMapperImpl());

        when(productRepository.findByIdAndStatusIn(eq(PRODUCT_ID), anyCollection()))
                .thenReturn(Optional.of(Product.builder().id(PRODUCT_ID).status(CatalogStatus.ACTIVE).build()));
        when(assembler.detail(any(), anyCollection())).thenReturn(ProductDetailResponse.builder().id(PRODUCT_ID).build());
        when(variantRepository.findByProductIdForUpdate(PRODUCT_ID, CatalogStatus.DELETED)).thenAnswer(invocation -> variants);
        when(variantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> {
            ProductVariant saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(variantOptionRepository.findByProductVariantIdIn(anyCollection())).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(0);
            return ids.stream().flatMap(id -> storedOptions.getOrDefault(id, List.of()).stream()
                    .map(option -> VariantOption.builder().productVariantId(id).optionId(option.getId()).build())).toList();
        });
        when(optionRepository.findAllById(anyCollection())).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(0);
            return storedOptions.values().stream().flatMap(List::stream).filter(option -> ids.contains(option.getId())).toList();
        });
        when(optionRepository.findByNameAndValueIgnoringCase(anyString(), anyString())).thenAnswer(invocation ->
                Optional.of(Option.builder().id(UUID.randomUUID()).name(invocation.getArgument(0)).value(invocation.getArgument(1)).build()));
        when(orderServiceClient.findOrderedVariants(any())).thenReturn(ApiResponse.success(List.of()));
    }

    private ProductVariant givenVariant(UUID id, CatalogStatus status, int quantity, String sku, VariantOptionRequest... options) {
        ProductVariant variant = ProductVariant.builder().id(id).productId(PRODUCT_ID).sku(sku).quantity(quantity)
                .price(100L).warrantyMonths(12).status(status).createdBy(EMPLOYEE_ID).build();
        variants.add(variant);
        storedOptions.put(id, Arrays.stream(options)
                .map(option -> Option.builder().id(UUID.randomUUID()).name(option.name()).value(option.value()).build()).toList());
        return variant;
    }

    private void givenOrdered(UUID... ids) {
        when(orderServiceClient.findOrderedVariants(any())).thenReturn(ApiResponse.success(List.of(ids)));
    }

    private static VariationItemRequest item(UUID id, String sku, Integer quantity, VariantOptionRequest... options) {
        return new VariationItemRequest(id, 200L, quantity, sku, null, null, 24, null, null, null, List.of(options));
    }

    private static VariantOptionRequest color(String value) {
        return new VariantOptionRequest("Color", value);
    }

    private ProductDetailResponse save(VariationItemRequest... items) {
        return service.saveVariations(PRODUCT_ID, new SaveVariationsRequest(List.of(items)), EMPLOYEE_ID);
    }

    @Test
    void productWithoutGroupsGainsColorsAndKeepsItsVariantStockAndId() {
        ProductVariant single = givenVariant(DEFAULT_ID, CatalogStatus.ACTIVE, 5, "X");
        givenOrdered(DEFAULT_ID);

        save(item(DEFAULT_ID, "X-BLACK", 99, color("Black")), item(null, "X-WHITE", 10, color("White")));

        assertEquals(5, single.getQuantity());
        assertEquals("X-BLACK", single.getSku());
        ArgumentCaptor<ProductVariant> saved = ArgumentCaptor.forClass(ProductVariant.class);
        verify(variantRepository, times(2)).save(saved.capture());
        ProductVariant white = saved.getAllValues().get(1);
        assertEquals(10, white.getQuantity());
        assertEquals(CatalogStatus.ACTIVE, white.getStatus());
        verify(variantOptionRepository).deleteByProductVariantId(DEFAULT_ID);
    }

    @Test
    void productWithGroupsDropsBackToOneVariant() {
        ProductVariant black = givenVariant(DEFAULT_ID, CatalogStatus.ACTIVE, 3, "X-BLACK", color("Black"));
        ProductVariant white = givenVariant(WHITE_ID, CatalogStatus.ACTIVE, 0, "X-WHITE", color("White"));

        save(item(DEFAULT_ID, "X", null));

        assertEquals(CatalogStatus.ACTIVE, black.getStatus());
        assertEquals(CatalogStatus.DELETED, white.getStatus());
        verify(variantOptionRepository).deleteByProductVariantId(DEFAULT_ID);
    }

    @Test
    void aVariantLeftOutIsHiddenWhenItHasStockOrOrders() {
        givenVariant(DEFAULT_ID, CatalogStatus.ACTIVE, 0, "X-BLACK", color("Black"));
        ProductVariant withStock = givenVariant(WHITE_ID, CatalogStatus.ACTIVE, 4, "X-WHITE", color("White"));
        UUID orderedId = UUID.randomUUID();
        ProductVariant ordered = givenVariant(orderedId, CatalogStatus.ACTIVE, 0, "X-GREY", color("Grey"));
        givenOrdered(orderedId);

        save(item(DEFAULT_ID, "X-BLACK", null, color("Black")));

        assertEquals(CatalogStatus.INACTIVE, withStock.getStatus());
        assertEquals(CatalogStatus.INACTIVE, ordered.getStatus());
    }

    @Test
    void aHiddenVariantLeftOutStaysAsItIs() {
        givenVariant(DEFAULT_ID, CatalogStatus.ACTIVE, 0, "X-BLACK", color("Black"));
        ProductVariant hidden = givenVariant(WHITE_ID, CatalogStatus.INACTIVE, 0, "X-OLD");

        save(item(DEFAULT_ID, "X-BLACK", null, color("Black")));

        assertEquals(CatalogStatus.INACTIVE, hidden.getStatus());
    }

    @Test
    void anOrderedVariantCannotGetAnotherOptionValue() {
        givenVariant(DEFAULT_ID, CatalogStatus.ACTIVE, 2, "X-BLACK", color("Black"));
        givenOrdered(DEFAULT_ID);

        BusinessException ex = assertThrows(BusinessException.class, () -> save(item(DEFAULT_ID, "X-BLACK", null, color("Grey"))));

        assertEquals(CatalogErrorCode.VARIANT_OPTIONS_LOCKED, ex.getErrorCode());
        verify(variantRepository, never()).save(any());
    }

    @Test
    void allListedVariantsMustShareTheOptionNames() {
        assertEquals(CatalogErrorCode.VARIANT_OPTIONS_MISMATCH, assertThrows(BusinessException.class, () -> save(
                item(null, "A", 1, color("Black")), item(null, "B", 1, new VariantOptionRequest("RAM", "16GB")))).getErrorCode());
    }

    @Test
    void listedCombinationsMustDiffer() {
        assertEquals(CatalogErrorCode.DUPLICATE_VARIANT_COMBINATION, assertThrows(BusinessException.class, () -> save(
                item(null, "A", 1, color("Black")), item(null, "B", 1, color(" black ")))).getErrorCode());
    }

    @Test
    void aSkuListedTwiceOrUsedByAnotherVariantIsRejected() {
        assertEquals(CatalogErrorCode.RESOURCE_CONFLICT, assertThrows(BusinessException.class, () -> save(
                item(null, "A", 1, color("Black")), item(null, "A", 1, color("White")))).getErrorCode());

        ProductVariant elsewhere = ProductVariant.builder().id(UUID.randomUUID()).sku("TAKEN").build();
        when(variantRepository.findBySkuInAndStatusNot(anyCollection(), eq(CatalogStatus.DELETED))).thenReturn(List.of(elsewhere));
        assertEquals(CatalogErrorCode.RESOURCE_CONFLICT, assertThrows(BusinessException.class,
                () -> save(item(null, "TAKEN", 1))).getErrorCode());
    }

    @Test
    void aVariantKeepingItsOwnSkuIsNoConflict() {
        ProductVariant black = givenVariant(DEFAULT_ID, CatalogStatus.ACTIVE, 1, "X-BLACK", color("Black"));
        when(variantRepository.findBySkuInAndStatusNot(anyCollection(), eq(CatalogStatus.DELETED))).thenReturn(List.of(black));

        save(item(DEFAULT_ID, "X-BLACK", null, color("Black")));

        verify(variantRepository).save(black);
    }

    @Test
    void aVariantListedTwiceIsRejected() {
        givenVariant(DEFAULT_ID, CatalogStatus.ACTIVE, 1, "X-BLACK", color("Black"));

        assertEquals(CatalogErrorCode.VARIANT_LISTED_TWICE, assertThrows(BusinessException.class, () -> save(
                item(DEFAULT_ID, "A", null, color("Black")), item(DEFAULT_ID, "B", null, color("White")))).getErrorCode());
    }

    @Test
    void anIdOfAnotherProductIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> save(item(UUID.randomUUID(), "A", null)));
    }
}
