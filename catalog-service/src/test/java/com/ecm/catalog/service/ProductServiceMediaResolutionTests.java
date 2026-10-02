package com.ecm.catalog.service;

import com.ecm.catalog.client.MediaServiceClient;
import com.ecm.catalog.dto.response.MediaFileResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Product;
import com.ecm.catalog.entity.ProductImage;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.mapper.OptionMapper;
import com.ecm.catalog.mapper.ProductImageMapper;
import com.ecm.catalog.mapper.ProductMapper;
import com.ecm.catalog.mapper.ProductVariantMapper;
import com.ecm.catalog.repository.BrandRepository;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.catalog.repository.OptionRepository;
import com.ecm.catalog.repository.VariantOptionRepository;
import com.ecm.catalog.repository.ProductImageRepository;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceMediaResolutionTests {

    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private OptionRepository optionRepository;
    @Mock private VariantOptionRepository variantOptionRepository;
    @Mock private ProductMapper productMapper;
    @Mock private ProductVariantMapper productVariantMapper;
    @Mock private ProductImageMapper productImageMapper;
    @Mock private OptionMapper optionMapper;
    @Mock private MediaServiceClient mediaServiceClient;
    @InjectMocks private ProductService productService;

    @Test
    void productDetailsResolveImagesWithOneBatchedMediaLookup() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID missingFileId = UUID.randomUUID();
        String url = "https://res.cloudinary.com/example/image/upload/product.jpg";
        Product product = Product.builder().id(productId).categoryId(UUID.randomUUID())
                .name("Test product").seoName("test-product").status(CatalogStatus.ACTIVE).build();
        ProductVariant variant = ProductVariant.builder().id(variantId).productId(productId)
                .listPrice(100L).quantity(1).sku("TEST-SKU").warrantyMonths(12)
                .status(CatalogStatus.ACTIVE).createdBy(UUID.randomUUID()).build();
        ProductImage image = ProductImage.builder().id(UUID.randomUUID()).productVariantId(variantId)
                .fileId(fileId).isMain(true).status(CatalogStatus.ACTIVE).build();
        ProductImage withoutMediaRecord = ProductImage.builder().id(UUID.randomUUID()).productVariantId(variantId)
                .fileId(missingFileId).isMain(false).status(CatalogStatus.ACTIVE).build();
        when(productMapper.toDetailResponse(product)).thenReturn(new com.ecm.catalog.dto.response.ProductDetailResponse());
        when(productVariantMapper.toResponse(variant)).thenReturn(new com.ecm.catalog.dto.response.ProductVariantResponse());
        when(productImageMapper.toResponse(image)).thenReturn(com.ecm.catalog.dto.response.ProductImageResponse.builder().fileId(fileId).build());
        when(productImageMapper.toResponse(withoutMediaRecord)).thenReturn(com.ecm.catalog.dto.response.ProductImageResponse.builder().fileId(missingFileId).build());
        when(productRepository.findByIdAndStatus(productId, CatalogStatus.ACTIVE)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProductIdAndStatus(productId, CatalogStatus.ACTIVE))
                .thenReturn(List.of(variant));
        when(productImageRepository.findByProductVariantIdInAndStatus(List.of(variantId), CatalogStatus.ACTIVE))
                .thenReturn(List.of(image, withoutMediaRecord));
        when(mediaServiceClient.getFiles(anyList())).thenReturn(ApiResponse.success(List.of(
                new MediaFileResponse(fileId, "product.jpg", "image/jpeg", 100L, url, Instant.now()),
                new MediaFileResponse(missingFileId, "missing.jpg", "image/jpeg", 100L, null, Instant.now())
        )));

        var response = productService.getProductById(productId);

        assertThat(response.getVariants()).hasSize(1);
        assertThat(response.getVariants().getFirst().getImages())
                .extracting("url").containsExactly(url, null);
        verify(mediaServiceClient, times(1)).getFiles(List.of(fileId, missingFileId));
    }

    @Test
    void variantWithoutImagesDoesNotCallMediaService() {
        UUID variantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ProductVariant variant = ProductVariant.builder().id(variantId).productId(productId)
                .listPrice(100L).quantity(1).sku("TEST-SKU").warrantyMonths(12)
                .status(CatalogStatus.ACTIVE).createdBy(UUID.randomUUID()).build();
        when(productVariantRepository.findByIdAndStatus(variantId, CatalogStatus.ACTIVE)).thenReturn(Optional.of(variant));
        when(productVariantMapper.toResponse(variant)).thenReturn(new com.ecm.catalog.dto.response.ProductVariantResponse());
        when(productImageRepository.findByProductVariantIdAndStatus(variantId, CatalogStatus.ACTIVE)).thenReturn(List.of());

        var response = productService.getProductVariant(productId, variantId);

        assertThat(response.getImages()).isEmpty();
        verifyNoInteractions(mediaServiceClient);
    }
}
