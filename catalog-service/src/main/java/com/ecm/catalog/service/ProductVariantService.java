package com.ecm.catalog.service;

import com.ecm.catalog.dto.response.ProductVariantResponse;
import com.ecm.catalog.mapper.ProductVariantMapper;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductVariantMapper productVariantMapper;

    public ProductVariantResponse getById(UUID id) {
        return productVariantMapper.toResponse(productVariantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", id)));
    }
}
