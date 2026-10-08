package com.ecm.catalog.service;

import com.ecm.catalog.client.OrderServiceClient;
import com.ecm.catalog.dto.request.CreateProductRequest;
import com.ecm.catalog.dto.request.ProductImageRequest;
import com.ecm.catalog.dto.request.UpdateProductRequest;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Product;
import com.ecm.catalog.entity.ProductImage;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.repository.BrandRepository;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.catalog.repository.ProductImageRepository;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID BRAND_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a4");
    private static final UUID FILE_1 = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID FILE_2 = UUID.fromString("00000000-0000-0000-0000-0000000000f2");

    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private ProductImageRepository imageRepository;
    private BrandRepository brandRepository;
    private CategoryRepository categoryRepository;
    private OrderServiceClient orderServiceClient;
    private ProductSpecificationValidator specificationValidator;
    private ProductMediaResolver mediaResolver;
    private ProductAssembler assembler;
    private ProductService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        imageRepository = mock(ProductImageRepository.class);
        brandRepository = mock(BrandRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        orderServiceClient = mock(OrderServiceClient.class);
        specificationValidator = mock(ProductSpecificationValidator.class);
        mediaResolver = mock(ProductMediaResolver.class);
        assembler = mock(ProductAssembler.class);
        service = new ProductService(productRepository, variantRepository, imageRepository, brandRepository, categoryRepository,
                orderServiceClient, specificationValidator, mediaResolver, assembler);

        when(categoryRepository.existsByIdAndStatus(CATEGORY_ID, CatalogStatus.ACTIVE)).thenReturn(true);
        when(brandRepository.existsByIdAndStatus(BRAND_ID, CatalogStatus.ACTIVE)).thenReturn(true);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(PRODUCT_ID);
            }
            return saved;
        });
    }

    private static CreateProductRequest createRequest(List<ProductImageRequest> images) {
        return new CreateProductRequest(" Corsair Vengeance ", null, BRAND_ID, CATEGORY_ID, Map.of("capacity_gb", 16), "desc", images);
    }

    private static Product existingProduct(CatalogStatus status) {
        return Product.builder().id(PRODUCT_ID).name("Old").seoName("old").categoryId(CATEGORY_ID).status(status).createdBy(EMPLOYEE_ID).build();
    }

    private static ProductVariant variant(int quantity) {
        return ProductVariant.builder().id(UUID.randomUUID()).productId(PRODUCT_ID).quantity(quantity).status(CatalogStatus.ACTIVE).build();
    }

    // ---- UC-ADM-PROD-003 add product ----

    @Test
    void createStoresActiveProductWithValidatedSpecificationsGalleryAndCreator() {
        when(specificationValidator.validate(eq(CATEGORY_ID), any())).thenReturn(Map.of("capacity_gb", 16));

        service.createProduct(createRequest(List.of(new ProductImageRequest(FILE_1, true), new ProductImageRequest(FILE_2, false))), EMPLOYEE_ID);

        ArgumentCaptor<Product> product = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(product.capture());
        assertEquals("Corsair Vengeance", product.getValue().getName());
        assertEquals("corsair-vengeance", product.getValue().getSeoName());
        assertEquals(CatalogStatus.ACTIVE, product.getValue().getStatus());
        assertEquals(EMPLOYEE_ID, product.getValue().getCreatedBy());
        assertEquals(Map.of("capacity_gb", 16), product.getValue().getSpecifications());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductImage>> images = ArgumentCaptor.forClass(List.class);
        verify(imageRepository).saveAll(images.capture());
        assertEquals(2, images.getValue().size());
        assertEquals(PRODUCT_ID, images.getValue().get(0).getProductId());
        assertEquals(true, images.getValue().get(0).isMain());
        verify(mediaResolver).requireExisting(anyCollection());
    }

    @Test
    void createWithoutImagesSkipsGalleryAndMediaLookup() {
        service.createProduct(createRequest(null), EMPLOYEE_ID);

        verify(imageRepository, never()).saveAll(any());
        verify(mediaResolver, never()).requireExisting(any());
    }

    @Test
    void createRejectsInactiveOrUnknownCategoryAndBrand() {
        when(categoryRepository.existsByIdAndStatus(CATEGORY_ID, CatalogStatus.ACTIVE)).thenReturn(false);
        assertEquals(CatalogErrorCode.INVALID_CATALOG_REFERENCE,
                assertThrows(BusinessException.class, () -> service.createProduct(createRequest(null), EMPLOYEE_ID)).getErrorCode());

        when(categoryRepository.existsByIdAndStatus(CATEGORY_ID, CatalogStatus.ACTIVE)).thenReturn(true);
        when(brandRepository.existsByIdAndStatus(BRAND_ID, CatalogStatus.ACTIVE)).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.createProduct(createRequest(null), EMPLOYEE_ID));
        verify(productRepository, never()).save(any());
    }

    @Test
    void createRejectsSeoNameOfNonDeletedProduct() {
        when(productRepository.existsBySeoNameAndStatusNot("corsair-vengeance", CatalogStatus.DELETED)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createProduct(createRequest(null), EMPLOYEE_ID));

        assertEquals(CatalogErrorCode.RESOURCE_CONFLICT, ex.getErrorCode());
        verify(productRepository, never()).save(any());
    }

    @Test
    void createRejectsMoreThanOneMainImageAndDuplicateFiles() {
        BusinessException twoMain = assertThrows(BusinessException.class, () -> service.createProduct(
                createRequest(List.of(new ProductImageRequest(FILE_1, true), new ProductImageRequest(FILE_2, true))), EMPLOYEE_ID));
        BusinessException duplicate = assertThrows(BusinessException.class, () -> service.createProduct(
                createRequest(List.of(new ProductImageRequest(FILE_1, true), new ProductImageRequest(FILE_1, false))), EMPLOYEE_ID));

        assertEquals(CatalogErrorCode.INVALID_GALLERY, twoMain.getErrorCode());
        assertEquals(CatalogErrorCode.INVALID_GALLERY, duplicate.getErrorCode());
        verify(productRepository, never()).save(any());
    }

    @Test
    void createRejectsUnknownImageFile() {
        doThrow(new BusinessException(CatalogErrorCode.INVALID_MEDIA_FILE)).when(mediaResolver).requireExisting(anyCollection());

        assertThrows(BusinessException.class,
                () -> service.createProduct(createRequest(List.of(new ProductImageRequest(FILE_1, true))), EMPLOYEE_ID));
        verify(productRepository, never()).save(any());
    }

    @Test
    void createPropagatesSpecificationErrors() {
        when(specificationValidator.validate(eq(CATEGORY_ID), any()))
                .thenThrow(new BusinessException(CatalogErrorCode.INVALID_SPECIFICATIONS, "capacity_gb: is required"));

        assertEquals(CatalogErrorCode.INVALID_SPECIFICATIONS,
                assertThrows(BusinessException.class, () -> service.createProduct(createRequest(null), EMPLOYEE_ID)).getErrorCode());
        verify(productRepository, never()).save(any());
    }

    // ---- UC-ADM-PROD-004 edit product ----

    @Test
    void updateChangesFieldsRecordsEditorAndKeepsGalleryWhenImagesIsNull() {
        Product product = existingProduct(CatalogStatus.ACTIVE);
        when(productRepository.findByIdAndStatusIn(eq(PRODUCT_ID), anyCollection())).thenReturn(Optional.of(product));

        service.updateProduct(PRODUCT_ID, new UpdateProductRequest("New name", null, null, CATEGORY_ID, null, "new", null), EMPLOYEE_ID);

        assertEquals("New name", product.getName());
        assertEquals("new-name", product.getSeoName());
        assertEquals(EMPLOYEE_ID, product.getUpdatedBy());
        assertNotNull(product.getUpdatedAt());
        verify(imageRepository, never()).deleteByProductId(any());
        verify(imageRepository, never()).saveAll(any());
    }

    @Test
    void updateReplacesGalleryWhenImagesAreGiven() {
        when(productRepository.findByIdAndStatusIn(eq(PRODUCT_ID), anyCollection())).thenReturn(Optional.of(existingProduct(CatalogStatus.ACTIVE)));

        service.updateProduct(PRODUCT_ID, new UpdateProductRequest("Old", null, null, CATEGORY_ID, null, null,
                List.of(new ProductImageRequest(FILE_2, true))), EMPLOYEE_ID);

        verify(imageRepository).deleteByProductId(PRODUCT_ID);
        verify(imageRepository).saveAll(any());
    }

    @Test
    void updateWithEmptyImagesClearsGallery() {
        when(productRepository.findByIdAndStatusIn(eq(PRODUCT_ID), anyCollection())).thenReturn(Optional.of(existingProduct(CatalogStatus.ACTIVE)));

        service.updateProduct(PRODUCT_ID, new UpdateProductRequest("Old", null, null, CATEGORY_ID, null, null, List.of()), EMPLOYEE_ID);

        verify(imageRepository).deleteByProductId(PRODUCT_ID);
        verify(imageRepository, never()).saveAll(any());
    }

    @Test
    void updateRejectsSeoNameOfAnotherProductAndTwoMainImages() {
        when(productRepository.findByIdAndStatusIn(eq(PRODUCT_ID), anyCollection())).thenReturn(Optional.of(existingProduct(CatalogStatus.ACTIVE)));
        when(productRepository.existsBySeoNameAndIdNotAndStatusNot("taken", PRODUCT_ID, CatalogStatus.DELETED)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.updateProduct(PRODUCT_ID,
                new UpdateProductRequest("Taken", null, null, CATEGORY_ID, null, null, null), EMPLOYEE_ID));

        assertThrows(BusinessException.class, () -> service.updateProduct(PRODUCT_ID,
                new UpdateProductRequest("Fine", null, null, CATEGORY_ID, null, null,
                        List.of(new ProductImageRequest(FILE_1, true), new ProductImageRequest(FILE_2, true))), EMPLOYEE_ID));
        verify(imageRepository, never()).deleteByProductId(any());
    }

    @Test
    void updateOfMissingOrDeletedProductIsNotFound() {
        when(productRepository.findByIdAndStatusIn(eq(PRODUCT_ID), anyCollection())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateProduct(PRODUCT_ID,
                new UpdateProductRequest("Old", null, null, CATEGORY_ID, null, null, null), EMPLOYEE_ID));
    }

    // ---- UC-ADM-PROD-006 hide / show ----

    @Test
    void hideSetsInactiveAndRecordsEditor() {
        Product product = existingProduct(CatalogStatus.ACTIVE);
        when(productRepository.findByIdAndStatusIn(eq(PRODUCT_ID), anyCollection())).thenReturn(Optional.of(product));

        service.updateProductStatus(PRODUCT_ID, CatalogStatus.INACTIVE, EMPLOYEE_ID);

        assertEquals(CatalogStatus.INACTIVE, product.getStatus());
        assertEquals(EMPLOYEE_ID, product.getUpdatedBy());
    }

    @Test
    void statusEndpointCannotDelete() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateProductStatus(PRODUCT_ID, CatalogStatus.DELETED, EMPLOYEE_ID));

        assertEquals(CatalogErrorCode.INVALID_PRODUCT_STATUS, ex.getErrorCode());
    }

    // ---- UC-ADM-PROD-005 delete product ----

    private static ApiResponse<Boolean> history(boolean value) {
        return ApiResponse.success(value);
    }

    @Test
    void deleteSoftDeletesProductAndItsVariantsWhenNoStockAndNoOrders() {
        Product product = existingProduct(CatalogStatus.ACTIVE);
        ProductVariant first = variant(0);
        ProductVariant second = variant(0);
        when(productRepository.findByIdAndStatusIn(eq(PRODUCT_ID), anyCollection())).thenReturn(Optional.of(product));
        when(variantRepository.findByProductIdAndStatusNot(PRODUCT_ID, CatalogStatus.DELETED)).thenReturn(List.of(first, second));
        when(orderServiceClient.hasOrderHistory(any())).thenReturn(history(false));

        service.deleteProduct(PRODUCT_ID, EMPLOYEE_ID);

        assertEquals(CatalogStatus.DELETED, product.getStatus());
        assertEquals(CatalogStatus.DELETED, first.getStatus());
        assertEquals(CatalogStatus.DELETED, second.getStatus());
        assertEquals(EMPLOYEE_ID, second.getUpdatedBy());
        verify(variantRepository).saveAll(List.of(first, second));
    }

    @Test
    void deleteIsBlockedWhenAVariantHoldsStock() {
        Product product = existingProduct(CatalogStatus.ACTIVE);
        when(productRepository.findByIdAndStatusIn(eq(PRODUCT_ID), anyCollection())).thenReturn(Optional.of(product));
        when(variantRepository.findByProductIdAndStatusNot(PRODUCT_ID, CatalogStatus.DELETED)).thenReturn(List.of(variant(3)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteProduct(PRODUCT_ID, EMPLOYEE_ID));

        assertEquals(CatalogErrorCode.PRODUCT_IN_USE, ex.getErrorCode());
        assertEquals(CatalogStatus.ACTIVE, product.getStatus());
    }

    @Test
    void deleteIsBlockedWhenAVariantWasOrdered() {
        Product product = existingProduct(CatalogStatus.ACTIVE);
        when(productRepository.findByIdAndStatusIn(eq(PRODUCT_ID), anyCollection())).thenReturn(Optional.of(product));
        when(variantRepository.findByProductIdAndStatusNot(PRODUCT_ID, CatalogStatus.DELETED)).thenReturn(List.of(variant(0)));
        when(orderServiceClient.hasOrderHistory(any())).thenReturn(history(true));

        assertThrows(BusinessException.class, () -> service.deleteProduct(PRODUCT_ID, EMPLOYEE_ID));
        assertEquals(CatalogStatus.ACTIVE, product.getStatus());
        verify(variantRepository, never()).saveAll(any());
    }

    @Test
    void deleteWithoutVariantsNeedsNoOrderLookup() {
        Product product = existingProduct(CatalogStatus.INACTIVE);
        when(productRepository.findByIdAndStatusIn(eq(PRODUCT_ID), anyCollection())).thenReturn(Optional.of(product));
        when(variantRepository.findByProductIdAndStatusNot(PRODUCT_ID, CatalogStatus.DELETED)).thenReturn(List.of());

        service.deleteProduct(PRODUCT_ID, EMPLOYEE_ID);

        assertEquals(CatalogStatus.DELETED, product.getStatus());
        verify(orderServiceClient, never()).hasOrderHistory(any());
    }
}
