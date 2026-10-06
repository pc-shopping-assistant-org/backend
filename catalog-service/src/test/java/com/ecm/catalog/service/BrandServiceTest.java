package com.ecm.catalog.service;

import com.ecm.catalog.dto.request.CreateBrandRequest;
import com.ecm.catalog.dto.request.UpdateBrandRequest;
import com.ecm.catalog.entity.Brand;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.mapper.BrandMapperImpl;
import com.ecm.catalog.repository.BrandRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrandServiceTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID IMAGE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private BrandRepository brandRepository;
    private ProductRepository productRepository;
    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandRepository = mock(BrandRepository.class);
        productRepository = mock(ProductRepository.class);
        brandService = new BrandService(brandRepository, new BrandMapperImpl(), productRepository);
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Brand brand(String name, CatalogStatus status) {
        return Brand.builder().id(ID).name(name).seoName(name.toLowerCase()).status(status).build();
    }

    @Test
    void createStoresActiveBrandWithImageAndSeoName() {
        brandService.create(new CreateBrandRequest(" Asus ROG ", null, "desc", IMAGE_ID));

        ArgumentCaptor<Brand> saved = ArgumentCaptor.forClass(Brand.class);
        verify(brandRepository).save(saved.capture());
        assertEquals("Asus ROG", saved.getValue().getName());
        assertEquals("asus-rog", saved.getValue().getSeoName());
        assertEquals(IMAGE_ID, saved.getValue().getImageFileId());
        assertEquals(CatalogStatus.ACTIVE, saved.getValue().getStatus());
    }

    @Test
    void createRejectsNameHeldByNonDeletedBrandButNotByDeletedOne() {
        when(brandRepository.existsByNameIgnoreCaseAndStatusNot("Asus", CatalogStatus.DELETED)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> brandService.create(new CreateBrandRequest("Asus", null, null, null)));

        assertEquals(CatalogErrorCode.RESOURCE_CONFLICT, ex.getErrorCode());
        verify(brandRepository, never()).save(any());
    }

    @Test
    void updateChangesFieldsAndImage() {
        Brand existing = brand("Old", CatalogStatus.ACTIVE);
        when(brandRepository.findById(ID)).thenReturn(Optional.of(existing));

        brandService.update(ID, new UpdateBrandRequest("New", null, "new desc", IMAGE_ID));

        assertEquals("New", existing.getName());
        assertEquals("new desc", existing.getDescription());
        assertEquals(IMAGE_ID, existing.getImageFileId());
    }

    @Test
    void updateRejectsNameOfAnotherBrand() {
        when(brandRepository.findById(ID)).thenReturn(Optional.of(brand("Old", CatalogStatus.ACTIVE)));
        when(brandRepository.existsByNameIgnoreCaseAndStatusNot("Taken", CatalogStatus.DELETED)).thenReturn(true);

        assertThrows(BusinessException.class, () -> brandService.update(ID, new UpdateBrandRequest("Taken", null, null, null)));
    }

    @Test
    void updateOfDeletedBrandIsNotFound() {
        when(brandRepository.findById(ID)).thenReturn(Optional.of(brand("Gone", CatalogStatus.DELETED)));

        assertThrows(ResourceNotFoundException.class, () -> brandService.update(ID, new UpdateBrandRequest("Gone", null, null, null)));
    }

    @Test
    void statusEndpointCannotSoftDelete() {
        when(brandRepository.findById(ID)).thenReturn(Optional.of(brand("Asus", CatalogStatus.ACTIVE)));

        assertThrows(BusinessException.class, () -> brandService.updateStatus(ID, CatalogStatus.DELETED));
        verify(brandRepository, never()).save(any());
    }

    @Test
    void deleteIsBlockedWhileNonDeletedProductsUseTheBrand() {
        Brand existing = brand("Asus", CatalogStatus.ACTIVE);
        when(brandRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(productRepository.existsByBrandIdAndStatusNot(ID, CatalogStatus.DELETED)).thenReturn(true);

        assertThrows(BusinessException.class, () -> brandService.delete(ID));
        assertEquals(CatalogStatus.ACTIVE, existing.getStatus());
    }

    @Test
    void deleteSoftDeletesUnusedBrand() {
        Brand existing = brand("Asus", CatalogStatus.ACTIVE);
        when(brandRepository.findById(ID)).thenReturn(Optional.of(existing));

        brandService.delete(ID);

        assertEquals(CatalogStatus.DELETED, existing.getStatus());
    }

    @Test
    void adminListIncludesInactiveBrands() {
        when(brandRepository.findByStatusNot(CatalogStatus.DELETED)).thenReturn(List.of(brand("Hidden", CatalogStatus.INACTIVE)));

        assertEquals("INACTIVE", brandService.getAllBrandsForAdmin().get(0).getStatus());
    }
}
