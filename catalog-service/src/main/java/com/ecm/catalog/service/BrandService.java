package com.ecm.catalog.service;

import com.ecm.catalog.dto.response.BrandResponse;
import com.ecm.catalog.entity.Brand;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.mapper.BrandMapper;
import com.ecm.catalog.repository.BrandRepository;
import com.ecm.common.exception.ResourceNotFoundException;
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
