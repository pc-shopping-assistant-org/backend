package com.ecm.catalog.service;

import com.ecm.catalog.dto.response.CategoryResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Category;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.mapper.CategoryMapper;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.common.exception.ResourceNotFoundException;
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
