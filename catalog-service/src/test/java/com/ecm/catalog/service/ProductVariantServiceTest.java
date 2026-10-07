package com.ecm.catalog.service;

import com.ecm.catalog.config.CatalogProperties;
import com.ecm.catalog.client.OrderServiceClient;
import com.ecm.catalog.dto.request.CreateVariantRequest;
import com.ecm.catalog.dto.request.UpdateVariantRequest;
import com.ecm.catalog.dto.request.VariantOptionRequest;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Option;
import com.ecm.catalog.entity.Product;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.entity.VariantOption;
import com.ecm.catalog.exception.CatalogErrorCode;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductVariantServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID VARIANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
    private static final UUID FILE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private OptionRepository optionRepository;
    private VariantOptionRepository variantOptionRepository;
    private OrderServiceClient orderServiceClient;
    private ProductMediaResolver mediaResolver;
    private ProductAssembler assembler;
    private ProductVariantService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        optionRepository = mock(OptionRepository.class);
        variantOptionRepository = mock(VariantOptionRepository.class);
        orderServiceClient = mock(OrderServiceClient.class);
        mediaResolver = mock(ProductMediaResolver.class);
        assembler = mock(ProductAssembler.class);
        service = new ProductVariantService(productRepository, variantRepository, optionRepository, variantOptionRepository,
                orderServiceClient, mediaResolver, assembler, new CatalogProperties());

        when(productRepository.findByIdAndStatusIn(eq(PRODUCT_ID), anyCollection()))
                .thenReturn(Optional.of(Product.builder().id(PRODUCT_ID).status(CatalogStatus.ACTIVE).build()));
        when(variantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> {
            ProductVariant saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(VARIANT_ID);
            }
            return saved;
        });
        when(optionRepository.save(any(Option.class))).thenAnswer(invocation -> {
            Option option = invocation.getArgument(0);
            option.setId(UUID.randomUUID());
            return option;
        });
    }

    private static CreateVariantRequest createRequest(String sku, String barcode, UUID imageFileId, List<VariantOptionRequest> options) {
        return new CreateVariantRequest(1_000_000L, 5, sku, "M1", "desc", 36, barcode, null, imageFileId, options);
    }

    private static ProductVariant existingVariant(int quantity) {
        return ProductVariant.builder().id(VARIANT_ID).productId(PRODUCT_ID).sku("SKU-1").quantity(quantity)
                .status(CatalogStatus.ACTIVE).createdBy(EMPLOYEE_ID).build();
    }

    // ---- UC-ADM-PROD-007 add variant ----

    @Test
    void createSavesActiveVariantWithCreatorAndTrimmedCodes() {
        service.createVariant(PRODUCT_ID, createRequest(" SKU-1 ", " 8930000 ", null, null), EMPLOYEE_ID);

        ArgumentCaptor<ProductVariant> saved = ArgumentCaptor.forClass(ProductVariant.class);
        verify(variantRepository).save(saved.capture());
        assertEquals("SKU-1", saved.getValue().getSku());
        assertEquals("8930000", saved.getValue().getBarcode());
        assertEquals(CatalogStatus.ACTIVE, saved.getValue().getStatus());
        assertEquals(EMPLOYEE_ID, saved.getValue().getCreatedBy());
        assertEquals(1_000_000L, saved.getValue().getPrice());
    }

    @Test
    void createStoresBlankBarcodeAsNull() {
        service.createVariant(PRODUCT_ID, createRequest("SKU-1", "  ", null, null), EMPLOYEE_ID);

        ArgumentCaptor<ProductVariant> saved = ArgumentCaptor.forClass(ProductVariant.class);
        verify(variantRepository).save(saved.capture());
        assertNull(saved.getValue().getBarcode());
    }

    @Test
    void createReusesExistingOptionAndCreatesMissingOne() {
        Option blue = Option.builder().id(UUID.randomUUID()).name("Color").value("Blue").build();
        when(optionRepository.findByNameIgnoreCaseAndValueIgnoreCase("color", "blue")).thenReturn(Optional.of(blue));

        service.createVariant(PRODUCT_ID, createRequest("SKU-1", null, null,
                List.of(new VariantOptionRequest(" color ", " blue "), new VariantOptionRequest("Storage", "16GB"))), EMPLOYEE_ID);

        ArgumentCaptor<Option> created = ArgumentCaptor.forClass(Option.class);
        verify(optionRepository).save(created.capture());
        assertEquals("Storage", created.getValue().getName());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VariantOption>> links = ArgumentCaptor.forClass(List.class);
        verify(variantOptionRepository).saveAll(links.capture());
        assertEquals(2, links.getValue().size());
        assertEquals(blue.getId(), links.getValue().get(0).getOptionId());
        assertEquals(VARIANT_ID, links.getValue().get(0).getProductVariantId());
    }

    @Test
    void createRejectsTwoOptionsWithTheSameNameIgnoringCaseAndSpaces() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createVariant(PRODUCT_ID,
                createRequest("SKU-1", null, null, List.of(new VariantOptionRequest("Color", "Blue"),
                        new VariantOptionRequest(" color ", "Red"))), EMPLOYEE_ID));

        assertEquals(CatalogErrorCode.INVALID_VARIANT_OPTIONS, ex.getErrorCode());
        verify(variantRepository, never()).save(any());
    }

    @Test
    void createRejectsSkuOrBarcodeOfNonDeletedVariant() {
        when(variantRepository.existsBySkuAndStatusNot("SKU-1", CatalogStatus.DELETED)).thenReturn(true);
        assertEquals(CatalogErrorCode.RESOURCE_CONFLICT, assertThrows(BusinessException.class,
                () -> service.createVariant(PRODUCT_ID, createRequest("SKU-1", null, null, null), EMPLOYEE_ID)).getErrorCode());

        when(variantRepository.existsByBarcodeAndStatusNot("8930000", CatalogStatus.DELETED)).thenReturn(true);
        assertThrows(BusinessException.class,
                () -> service.createVariant(PRODUCT_ID, createRequest("SKU-2", "8930000", null, null), EMPLOYEE_ID));
        verify(variantRepository, never()).save(any());
    }

    @Test
    void createRejectsUnknownImageFile() {
        doThrow(new BusinessException(CatalogErrorCode.INVALID_MEDIA_FILE)).when(mediaResolver).requireExisting(anyCollection());

        assertThrows(BusinessException.class,
                () -> service.createVariant(PRODUCT_ID, createRequest("SKU-1", null, FILE_ID, null), EMPLOYEE_ID));
        verify(variantRepository, never()).save(any());
    }

    @Test
    void createForMissingProductIsNotFound() {
        when(productRepository.findByIdAndStatusIn(eq(PRODUCT_ID), anyCollection())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createVariant(PRODUCT_ID, createRequest("SKU-1", null, null, null), EMPLOYEE_ID));
    }

    // ---- edit / status / delete ----

    @Test
    void updateReplacesFieldsImageAndOptions() {
        ProductVariant variant = existingVariant(5);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));

        service.updateVariant(PRODUCT_ID, VARIANT_ID, new UpdateVariantRequest(2_000_000L, 7, "SKU-9", null, null, 24, null, null, FILE_ID,
                List.of(new VariantOptionRequest("Color", "Red"))), EMPLOYEE_ID);

        assertEquals(2_000_000L, variant.getPrice());
        assertEquals(7, variant.getQuantity());
        assertEquals("SKU-9", variant.getSku());
        assertEquals(FILE_ID, variant.getImageFileId());
        assertEquals(EMPLOYEE_ID, variant.getUpdatedBy());
        verify(variantOptionRepository).deleteByProductVariantId(VARIANT_ID);
        verify(variantOptionRepository).saveAll(any());
    }

    @Test
    void updateRejectsSkuUsedByAnotherVariant() {
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(existingVariant(5)));
        when(variantRepository.existsBySkuAndIdNotAndStatusNot("SKU-9", VARIANT_ID, CatalogStatus.DELETED)).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.updateVariant(PRODUCT_ID, VARIANT_ID,
                new UpdateVariantRequest(1L, 1, "SKU-9", null, null, 12, null, null, null, null), EMPLOYEE_ID));
        verify(variantOptionRepository, never()).deleteByProductVariantId(any());
    }

    @Test
    void variantOfAnotherProductOrDeletedIsNotFound() {
        ProductVariant foreign = existingVariant(0);
        foreign.setProductId(UUID.randomUUID());
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(foreign));
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateVariantStatus(PRODUCT_ID, VARIANT_ID, CatalogStatus.INACTIVE, EMPLOYEE_ID));

        ProductVariant deleted = existingVariant(0);
        deleted.setStatus(CatalogStatus.DELETED);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(deleted));
        assertThrows(ResourceNotFoundException.class, () -> service.deleteVariant(PRODUCT_ID, VARIANT_ID, EMPLOYEE_ID));
    }

    @Test
    void statusEndpointCannotDeleteVariant() {
        assertThrows(BusinessException.class,
                () -> service.updateVariantStatus(PRODUCT_ID, VARIANT_ID, CatalogStatus.DELETED, EMPLOYEE_ID));
    }

    @Test
    void deleteSoftDeletesVariantWithoutStockOrOrders() {
        ProductVariant variant = existingVariant(0);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant));
        when(orderServiceClient.hasOrderHistory(VARIANT_ID)).thenReturn(ApiResponse.success(false));

        service.deleteVariant(PRODUCT_ID, VARIANT_ID, EMPLOYEE_ID);

        assertEquals(CatalogStatus.DELETED, variant.getStatus());
        assertEquals(EMPLOYEE_ID, variant.getUpdatedBy());
    }

    @Test
    void deleteIsBlockedByStockOrOrderHistory() {
        ProductVariant withStock = existingVariant(2);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(withStock));
        assertEquals(CatalogErrorCode.PRODUCT_IN_USE,
                assertThrows(BusinessException.class, () -> service.deleteVariant(PRODUCT_ID, VARIANT_ID, EMPLOYEE_ID)).getErrorCode());

        ProductVariant ordered = existingVariant(0);
        when(variantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(ordered));
        when(orderServiceClient.hasOrderHistory(VARIANT_ID)).thenReturn(ApiResponse.success(true));
        assertThrows(BusinessException.class, () -> service.deleteVariant(PRODUCT_ID, VARIANT_ID, EMPLOYEE_ID));
        assertEquals(CatalogStatus.ACTIVE, ordered.getStatus());
    }
}
