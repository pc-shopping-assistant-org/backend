package com.ecm.catalog.controller;

import com.ecm.catalog.client.MediaServiceClient;
import com.ecm.catalog.dto.response.BrandResponse;
import com.ecm.catalog.dto.response.MediaFileResponse;
import com.ecm.catalog.service.BrandService;
import com.ecm.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;
    private final MediaServiceClient mediaServiceClient;

    @GetMapping
    public ApiResponse<List<BrandResponse>> getAllBrands() {
        List<BrandResponse> response = brandService.getAllBrands();
        List<UUID> imageFileIds = response.stream().map(BrandResponse::getImageFileId)
                .filter(id -> id != null).distinct().toList();
        if (!imageFileIds.isEmpty()) {
            var imageUrls = mediaServiceClient.getFiles(imageFileIds).getData().stream()
                    .filter(file -> file.url() != null)
                    .collect(java.util.stream.Collectors.toMap(MediaFileResponse::id, MediaFileResponse::url));
            response.forEach(brand -> brand.setImageUrl(imageUrls.get(brand.getImageFileId())));
        }
        return ApiResponse.success("Get brands successfully", response);
    }

    @GetMapping("/{id}")
    public ApiResponse<BrandResponse> getBrandById(@PathVariable UUID id) {
        BrandResponse response = brandService.getBrandById(id);
        if (response.getImageFileId() != null) {
            response.setImageUrl(mediaServiceClient.getFiles(List.of(response.getImageFileId()))
                    .getData().stream().findFirst().map(MediaFileResponse::url).orElse(null));
        }
        return ApiResponse.success("Get brand successfully", response);
    }
}
