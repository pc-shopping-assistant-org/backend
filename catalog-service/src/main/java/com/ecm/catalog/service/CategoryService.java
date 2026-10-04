package com.ecm.catalog.service;

import com.ecm.catalog.dto.response.CategoryResponse;
import com.ecm.catalog.dto.request.CreateCategoryRequest;
import com.ecm.catalog.dto.request.UpdateCategoryDetailsRequest;
import com.ecm.catalog.dto.request.UpdateCategoryRequest;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Category;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.mapper.CategoryMapper;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final ProductRepository productRepository;

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String seoName = normalizeSeo(request.seoName(), request.name());
        if (categoryRepository.existsByNameIgnoreCase(request.name().trim()) || categoryRepository.existsBySeoName(seoName)) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT);
        }
        validateParent(request.parentId(), null);
        Category category = Category.builder().name(request.name().trim()).seoName(seoName)
                .parentId(request.parentId()).status(CatalogStatus.ACTIVE).build();
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryDetailsRequest request) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
        if (category.getStatus() == CatalogStatus.DELETED) throw new BusinessException(CatalogErrorCode.INVALID_CATALOG_REFERENCE);
        String seoName = normalizeSeo(request.seoName(), request.name());
        if ((!category.getName().equalsIgnoreCase(request.name().trim()) && categoryRepository.existsByNameIgnoreCase(request.name().trim()))
                || (!category.getSeoName().equals(seoName) && categoryRepository.existsBySeoName(seoName))) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT);
        }
        validateParent(request.parentId(), id);
        category.setName(request.name().trim()); category.setSeoName(seoName); category.setParentId(request.parentId());
        category.setUpdatedAt(java.time.Instant.now());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
        update(id, new UpdateCategoryDetailsRequest(request.name(), request.seoName(), request.parentId()));
        Category category = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
        category.setStatus(request.status()); category.setUpdatedAt(java.time.Instant.now());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID id) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
        if (categoryRepository.existsByParentId(id) || productRepository.existsByCategoryIdAndStatusNot(id, CatalogStatus.DELETED)) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT, "Category has children or products");
        }
        category.setStatus(CatalogStatus.DELETED); category.setUpdatedAt(java.time.Instant.now());
        categoryRepository.save(category);
    }

    private void validateParent(UUID parentId, UUID selfId) {
        if (parentId == null) return;
        if (parentId.equals(selfId) || categoryRepository.findByIdAndStatus(parentId, CatalogStatus.ACTIVE).isEmpty()) {
            throw new BusinessException(CatalogErrorCode.INVALID_CATALOG_REFERENCE, "Parent category must be ACTIVE and cannot be itself");
        }
    }

    private String normalizeSeo(String seoName, String name) {
        return (seoName == null || seoName.isBlank() ? name : seoName).trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoryTree() {
        // 1. Fetch all active categories
        List<Category> allCategories = categoryRepository.findByStatus(CatalogStatus.ACTIVE);

        // 2. Map to response DTOs
        Map<UUID, CategoryResponse> categoryMap = allCategories.stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toMap(CategoryResponse::getId, c -> c));

        // 3. Build tree structure
        List<CategoryResponse> roots = new ArrayList<>();
        for (CategoryResponse category : categoryMap.values()) {
            if (category.getParentId() == null) {
                roots.add(category);
            } else {
                CategoryResponse parent = categoryMap.get(category.getParentId());
                if (parent != null) {
                    parent.getChildren().add(category);
                }
            }
        }

        // 4. Return root categories with nested children
        return roots;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        // 1. Fetch all active categories
        List<Category> categories = categoryRepository.findByStatus(CatalogStatus.ACTIVE);

        // 2. Map to response DTOs (flat list)
        return categoryMapper.toResponseList(categories);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID id) {
        // 1. Fetch category by ID
        Category category = categoryRepository.findByIdAndStatus(id, CatalogStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));

        // 2. Map to response DTO
        return categoryMapper.toResponse(category);
    }
}
