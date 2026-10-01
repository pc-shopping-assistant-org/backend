package com.ecm.catalog.controller;

import com.ecm.catalog.client.MediaServiceClient;
import com.ecm.catalog.dto.response.BrandResponse;
import com.ecm.catalog.dto.response.MediaFileResponse;
import com.ecm.catalog.service.BrandService;
import com.ecm.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrandControllerMediaResolutionTests {

    @Mock private BrandService brandService;
    @Mock private MediaServiceClient mediaServiceClient;
    @InjectMocks private BrandController brandController;

    @Test
    void brandListResolvesDistinctImageIdsInOneBatch() {
        UUID firstFileId = UUID.randomUUID();
        UUID secondFileId = UUID.randomUUID();
        String firstUrl = "https://res.cloudinary.com/example/image/upload/brand-a.jpg";
        String secondUrl = "https://res.cloudinary.com/example/image/upload/brand-b.jpg";
        BrandResponse firstBrand = BrandResponse.builder().imageFileId(firstFileId).build();
        BrandResponse secondBrand = BrandResponse.builder().imageFileId(firstFileId).build();
        BrandResponse thirdBrand = BrandResponse.builder().imageFileId(secondFileId).build();
        when(brandService.getAllBrands()).thenReturn(List.of(firstBrand, secondBrand, thirdBrand));
        when(mediaServiceClient.getFiles(List.of(firstFileId, secondFileId))).thenReturn(ApiResponse.success(List.of(
                new MediaFileResponse(firstFileId, "brand-a.jpg", "image/jpeg", 100L, firstUrl, Instant.now()),
                new MediaFileResponse(secondFileId, "brand-b.jpg", "image/jpeg", 100L, secondUrl, Instant.now())
        )));

        ApiResponse<List<BrandResponse>> response = brandController.getAllBrands();

        assertThat(response.getData()).extracting(BrandResponse::getImageUrl)
                .containsExactly(firstUrl, firstUrl, secondUrl);
        verify(mediaServiceClient, times(1)).getFiles(List.of(firstFileId, secondFileId));
    }

    @Test
    void brandWithoutImageDoesNotCallMediaService() {
        BrandResponse brand = BrandResponse.builder().build();
        when(brandService.getBrandById(any())).thenReturn(brand);

        ApiResponse<BrandResponse> response = brandController.getBrandById(UUID.randomUUID());

        assertThat(response.getData().getImageUrl()).isNull();
        verifyNoInteractions(mediaServiceClient);
    }

    @Test
    void singleBrandResolvesItsImageUrl() {
        UUID brandId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String url = "https://res.cloudinary.com/example/image/upload/brand.jpg";
        when(brandService.getBrandById(brandId))
                .thenReturn(BrandResponse.builder().id(brandId).imageFileId(fileId).build());
        when(mediaServiceClient.getFiles(List.of(fileId))).thenReturn(ApiResponse.success(List.of(
                new MediaFileResponse(fileId, "brand.jpg", "image/jpeg", 100L, url, Instant.now())
        )));

        ApiResponse<BrandResponse> response = brandController.getBrandById(brandId);

        assertThat(response.getData().getImageUrl()).isEqualTo(url);
    }
}
