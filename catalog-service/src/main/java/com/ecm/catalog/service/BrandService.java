package com.ecm.catalog.service;

import com.ecm.catalog.dto.response.BrandResponse;
import com.ecm.catalog.dto.request.CreateBrandRequest;
import com.ecm.catalog.dto.request.UpdateBrandRequest;
import com.ecm.catalog.dto.request.UpdateStatusRequest;
import com.ecm.catalog.entity.Brand;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.helper.MediaFileResolver;
import com.ecm.catalog.mapper.BrandMapper;
import com.ecm.catalog.repository.BrandRepository;
import com.ecm.catalog.util.SeoNames;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.exception.BusinessException;
import com.ecm.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final BrandMapper brandMapper;
    private final ProductRepository productRepository;
    private final MediaFileResolver mediaFileResolver;

    @Transactional
    public BrandResponse create(CreateBrandRequest request) {
        // 1. Reject a name or SEO name already held by a non-deleted brand
        String name = request.name().trim();
        String seoName = SeoNames.slug(request.seoName(), name);
        if (brandRepository.existsByNameIgnoreCaseAndStatusNot(name, CatalogStatus.DELETED)
                || brandRepository.existsBySeoNameAndStatusNot(seoName, CatalogStatus.DELETED)) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT);
        }

        // 2. Persist the new brand
        Brand brand = brandMapper.toEntity(request);
        brand.setName(name);
        brand.setSeoName(seoName);
        brand.setStatus(CatalogStatus.ACTIVE);
        return brandMapper.toResponse(brandRepository.save(brand));
    }

    @Transactional
    public BrandResponse update(UUID id, UpdateBrandRequest request) {
        // 1. Load the brand being edited
        Brand brand = findLiveBrand(id);

        // 2. Name and SEO name may only collide with themselves
        String name = request.name().trim();
        String seoName = SeoNames.slug(request.seoName(), name);
        boolean nameChanged = !brand.getName().equalsIgnoreCase(name);
        boolean seoNameChanged = !brand.getSeoName().equals(seoName);
        if ((nameChanged && brandRepository.existsByNameIgnoreCaseAndStatusNot(name, CatalogStatus.DELETED))
                || (seoNameChanged && brandRepository.existsBySeoNameAndStatusNot(seoName, CatalogStatus.DELETED))) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT);
        }

        // 3. Apply the edit
        brand.setName(name);
        brand.setSeoName(seoName);
        brand.setDescription(request.description());
        brand.setImageFileId(request.imageFileId());
        return brandMapper.toResponse(brandRepository.save(brand));
    }

    @Transactional
    public BrandResponse updateStatus(UUID id, CatalogStatus status) {
        Brand brand = findLiveBrand(id);
        if (status == CatalogStatus.DELETED) {
            throw new BusinessException(CatalogErrorCode.INVALID_PRODUCT_STATUS);
        }
        brand.setStatus(status);
        return brandMapper.toResponse(brandRepository.save(brand));
    }

    @Transactional
    public void delete(UUID id) {
        // 1. A brand still assigned to a non-deleted product cannot be removed
        Brand brand = findLiveBrand(id);
        if (productRepository.existsByBrandIdAndStatusNot(id, CatalogStatus.DELETED)) {
            throw new BusinessException(CatalogErrorCode.BRAND_IN_USE);
        }

        // 2. Soft delete so its name can be reused
        brand.setStatus(CatalogStatus.DELETED);
        brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> getAllBrandsForAdmin() {
        // 1. Admin listing includes INACTIVE brands, never DELETED ones
        List<BrandResponse> brands = brandMapper.toResponseList(brandRepository.findByStatusNot(CatalogStatus.DELETED));

        // 2. Add the image URLs
        return withImageUrls(brands);
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> getAllBrands() {
        // 1. Fetch all active brands
        List<Brand> brands = brandRepository.findByStatus(CatalogStatus.ACTIVE);

        // 2. Map to response DTOs and add the image URLs
        return withImageUrls(brandMapper.toResponseList(brands));
    }

    @Transactional(readOnly = true)
    public BrandResponse getBrandById(UUID id) {
        // 1. Fetch brand by ID
        Brand brand = brandRepository.findByIdAndStatus(id, CatalogStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", id));

        // 2. Map to response DTO and add the image URL
        return withImageUrls(List.of(brandMapper.toResponse(brand))).get(0);
    }

    private Brand findLiveBrand(UUID id) {
        Brand brand = brandRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Brand", id));
        if (brand.getStatus() == CatalogStatus.DELETED) {
            throw new ResourceNotFoundException("Brand", id);
        }
        return brand;
    }

    /** One batched Media Service call for the whole list; a brand without an image keeps a null URL. */
    private List<BrandResponse> withImageUrls(List<BrandResponse> brands) {
        Map<UUID, String> urls = mediaFileResolver.resolveUrls(brands.stream().map(BrandResponse::getImageFileId).toList());
        brands.forEach(brand -> brand.setImageUrl(brand.getImageFileId() == null ? null : urls.get(brand.getImageFileId())));
        return brands;
    }
}
