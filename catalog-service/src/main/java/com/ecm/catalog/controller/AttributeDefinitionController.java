package com.ecm.catalog.controller;

import com.ecm.catalog.dto.request.CreateAttributeDefinitionRequest;
import com.ecm.catalog.dto.request.UpdateAttributeDefinitionRequest;
import com.ecm.catalog.dto.response.AttributeDefinitionResponse;
import com.ecm.catalog.service.AttributeDefinitionService;
import com.ecm.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/attribute-definitions")
@RequiredArgsConstructor
public class AttributeDefinitionController {

    private final AttributeDefinitionService attributeService;

    @PostMapping
    public ApiResponse<AttributeDefinitionResponse> create(@Valid @RequestBody CreateAttributeDefinitionRequest request) {
        return ApiResponse.success(attributeService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AttributeDefinitionResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateAttributeDefinitionRequest request) {
        return ApiResponse.success(attributeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        attributeService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping
    public ApiResponse<List<AttributeDefinitionResponse>> getAll() {
        return ApiResponse.success(attributeService.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<AttributeDefinitionResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(attributeService.getById(id));
    }
}
