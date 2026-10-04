package com.ecm.catalog.service;

import com.ecm.catalog.dto.response.BrandResponse;
import com.ecm.catalog.dto.request.CreateBrandRequest;
import com.ecm.catalog.dto.request.UpdateBrandRequest;
import com.ecm.catalog.dto.request.UpdateStatusRequest;
import com.ecm.catalog.entity.Brand;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.mapper.BrandMapper;
import com.ecm.catalog.repository.BrandRepository;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.exception.BusinessException;
import com.ecm.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final BrandMapper brandMapper;
    private final ProductRepository productRepository;

    @Transactional
    public BrandResponse create(CreateBrandRequest request) {
        String seoName = normalizeSeo(request.seoName(), request.name());
        if (brandRepository.existsByNameIgnoreCase(request.name().trim()) || brandRepository.existsBySeoName(seoName)) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT);
        }
        Brand brand = brandMapper.toEntity(request);
        brand.setName(request.name().trim()); brand.setSeoName(seoName); brand.setStatus(CatalogStatus.ACTIVE);
        return brandMapper.toResponse(brandRepository.save(brand));
    }

    @Transactional
    public BrandResponse update(UUID id, UpdateBrandRequest request) {
        Brand brand = brandRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Brand", id));
        String seoName = normalizeSeo(request.seoName(), request.name());
        if ((!brand.getName().equalsIgnoreCase(request.name().trim()) && brandRepository.existsByNameIgnoreCase(request.name().trim()))
                || (!brand.getSeoName().equals(seoName) && brandRepository.existsBySeoName(seoName))) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT);
        }
        brand.setName(request.name().trim()); brand.setSeoName(seoName); brand.setDescription(request.description());
        brand.setImageFileId(request.imageFileId());
        return brandMapper.toResponse(brandRepository.save(brand));
    }

    @Transactional
    public BrandResponse updateStatus(UUID id, CatalogStatus status) {
        Brand brand = brandRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Brand", id));
        if (brand.getStatus() == CatalogStatus.DELETED || status == CatalogStatus.DELETED) throw new BusinessException(CatalogErrorCode.INVALID_PRODUCT_STATUS);
        brand.setStatus(status);
        return brandMapper.toResponse(brandRepository.save(brand));
    }

    @Transactional
    public void delete(UUID id) {
        Brand brand = brandRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Brand", id));
        if (productRepository.existsByBrandId(id)) throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT, "Brand is assigned to products");
        brand.setStatus(CatalogStatus.DELETED);
        brandRepository.save(brand);
    }

    private String normalizeSeo(String seoName, String name) {
        return (seoName == null || seoName.isBlank() ? name : seoName).trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> getAllBrands() {
        // 1. Fetch all active brands
        List<Brand> brands = brandRepository.findByStatus(CatalogStatus.ACTIVE);

        // 2. Map to response DTOs
        return brandMapper.toResponseList(brands);
    }

    @Transactional(readOnly = true)
    public BrandResponse getBrandById(UUID id) {
        // 1. Fetch brand by ID
        Brand brand = brandRepository.findByIdAndStatus(id, CatalogStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", id));

        // 2. Map to response DTO
        return brandMapper.toResponse(brand);
    }
}
