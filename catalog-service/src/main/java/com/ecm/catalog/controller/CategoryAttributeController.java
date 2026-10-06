package com.ecm.catalog.controller;

import com.ecm.catalog.dto.request.ReplaceCategoryAttributesRequest;
import com.ecm.catalog.dto.response.CategoryAttributeTemplateResponse;
import com.ecm.catalog.service.CategoryAttributeService;
import com.ecm.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/categories/{categoryId}/attributes")
@RequiredArgsConstructor
public class CategoryAttributeController {

    private final CategoryAttributeService categoryAttributeService;

    @GetMapping
    public ApiResponse<CategoryAttributeTemplateResponse> getTemplate(@PathVariable UUID categoryId) {
        return ApiResponse.success(categoryAttributeService.getTemplate(categoryId));
    }

    @PutMapping
    public ApiResponse<CategoryAttributeTemplateResponse> replaceTemplate(
            @PathVariable UUID categoryId, @Valid @RequestBody ReplaceCategoryAttributesRequest request) {
        return ApiResponse.success(categoryAttributeService.replaceTemplate(categoryId, request));
    }
}
