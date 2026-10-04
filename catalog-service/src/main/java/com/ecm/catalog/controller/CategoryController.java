package com.ecm.catalog.controller;

import com.ecm.catalog.dto.response.CategoryResponse;
import com.ecm.catalog.dto.request.CreateCategoryRequest;
import com.ecm.catalog.dto.request.UpdateCategoryDetailsRequest;
import com.ecm.catalog.dto.request.UpdateCategoryRequest;
import com.ecm.catalog.service.CategoryService;
import com.ecm.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ApiResponse<CategoryResponse> create(@jakarta.validation.Valid @RequestBody CreateCategoryRequest request) {
        return ApiResponse.success("Category created", categoryService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable UUID id, @jakarta.validation.Valid @RequestBody UpdateCategoryDetailsRequest request) {
        return ApiResponse.success("Category updated", categoryService.update(id, request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CategoryResponse> updateWithStatus(@PathVariable UUID id, @jakarta.validation.Valid @RequestBody UpdateCategoryRequest request) {
        return ApiResponse.success("Category updated", categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ApiResponse.success("Category deleted", null);
    }

    @GetMapping("/tree")
    public ApiResponse<List<CategoryResponse>> getCategoryTree() {
        List<CategoryResponse> response = categoryService.getCategoryTree();
        return ApiResponse.success("Get category tree successfully", response);
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getAllCategories() {
        List<CategoryResponse> response = categoryService.getAllCategories();
        return ApiResponse.success("Get categories successfully", response);
    }

    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> getCategoryById(@PathVariable UUID id) {
        CategoryResponse response = categoryService.getCategoryById(id);
        return ApiResponse.success("Get category successfully", response);
    }
}
