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
        // 1. Reject a name or SEO name already held by a non-deleted category
        String name = request.name().trim();
        String seoName = normalizeSeo(request.seoName(), name);
        ensureNameAvailable(name, seoName);

        // 2. The parent, when given, must be an ACTIVE category
        validateParent(request.parentId(), null);

        // 3. Persist the new category
        Category category = Category.builder().name(name).seoName(seoName).description(normalizeDescription(request.description()))
                .parentId(request.parentId()).status(CatalogStatus.ACTIVE).build();
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryDetailsRequest request) {
        Category category = applyDetails(id, request.name(), request.seoName(), request.description(), request.parentId());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
        // 1. Soft delete has its own endpoint, which enforces the "no products / no children" rule
        if (request.status() == CatalogStatus.DELETED) {
            throw new BusinessException(CatalogErrorCode.INVALID_CATALOG_REFERENCE, "Use DELETE to remove a category");
        }

        // 2. Apply the details and the requested status together
        Category category = applyDetails(id, request.name(), request.seoName(), request.description(), request.parentId());
        category.setStatus(request.status());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID id) {
        // 1. A category with live children or products cannot be removed
        Category category = findLiveCategory(id);
        if (categoryRepository.existsByParentIdAndStatusNot(id, CatalogStatus.DELETED)
                || productRepository.existsByCategoryIdAndStatusNot(id, CatalogStatus.DELETED)) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT, "Category has children or products");
        }

        // 2. Soft delete so its name can be reused
        category.setStatus(CatalogStatus.DELETED);
        categoryRepository.save(category);
    }

    private Category applyDetails(UUID id, String rawName, String rawSeoName, String description, UUID parentId) {
        // 1. Load the category being edited
        Category category = findLiveCategory(id);

        // 2. Name and SEO name may only collide with themselves
        String name = rawName.trim();
        String seoName = normalizeSeo(rawSeoName, name);
        boolean nameChanged = !category.getName().equalsIgnoreCase(name);
        boolean seoNameChanged = !category.getSeoName().equals(seoName);
        if ((nameChanged && categoryRepository.existsByNameIgnoreCaseAndStatusNot(name, CatalogStatus.DELETED))
                || (seoNameChanged && categoryRepository.existsBySeoNameAndStatusNot(seoName, CatalogStatus.DELETED))) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT);
        }

        // 3. Moving under a new parent must not create a cycle
        validateParent(parentId, id);

        category.setName(name);
        category.setSeoName(seoName);
        category.setDescription(normalizeDescription(description));
        category.setParentId(parentId);
        return category;
    }

    private Category findLiveCategory(UUID id) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
        if (category.getStatus() == CatalogStatus.DELETED) {
            throw new ResourceNotFoundException("Category", id);
        }
        return category;
    }

    private void ensureNameAvailable(String name, String seoName) {
        if (categoryRepository.existsByNameIgnoreCaseAndStatusNot(name, CatalogStatus.DELETED)
                || categoryRepository.existsBySeoNameAndStatusNot(seoName, CatalogStatus.DELETED)) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT);
        }
    }

    private void validateParent(UUID parentId, UUID selfId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId) || categoryRepository.findByIdAndStatus(parentId, CatalogStatus.ACTIVE).isEmpty()) {
            throw new BusinessException(CatalogErrorCode.INVALID_CATALOG_REFERENCE, "Parent category must be ACTIVE and cannot be itself");
        }
        if (selfId != null && isDescendant(parentId, selfId)) {
            throw new BusinessException(CatalogErrorCode.INVALID_CATALOG_REFERENCE, "Parent category cannot be a descendant of the category");
        }
    }

    // Walks up from candidateId; true when selfId is one of its ancestors.
    private boolean isDescendant(UUID candidateId, UUID selfId) {
        Set<UUID> visited = new HashSet<>();
        UUID current = candidateId;
        while (current != null && visited.add(current)) {
            if (current.equals(selfId)) {
                return true;
            }
            current = categoryRepository.findById(current).map(Category::getParentId).orElse(null);
        }
        return false;
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
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
    public List<CategoryResponse> getAllCategoriesForAdmin() {
        // 1. Admin listing includes INACTIVE categories, never DELETED ones
        return categoryMapper.toResponseList(categoryRepository.findByStatusNot(CatalogStatus.DELETED));
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
