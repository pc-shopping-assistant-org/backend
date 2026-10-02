package com.ecm.catalog.service;

import com.ecm.catalog.client.MediaServiceClient;
import com.ecm.catalog.dto.response.CartVariantDetailsResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Product;
import com.ecm.catalog.entity.ProductImage;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.repository.ProductImageRepository;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartVariantDetailsService {
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final MediaServiceClient mediaServiceClient;

    @Transactional(readOnly = true)
    public List<CartVariantDetailsResponse> getDetails(List<UUID> ids) {
        List<UUID> variantIds = ids == null ? List.of() : ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (variantIds.isEmpty()) return List.of();
        List<ProductVariant> variants = variantRepository.findAllById(variantIds);
        Map<UUID, Product> products = productRepository.findAllById(variants.stream().map(ProductVariant::getProductId)
                .distinct().toList()).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        List<UUID> existingIds = variants.stream().map(ProductVariant::getId).toList();
        List<ProductImage> images = imageRepository.findByProductVariantIdInAndStatus(existingIds, CatalogStatus.ACTIVE);
        List<UUID> fileIds = images.stream().map(ProductImage::getFileId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, String> urls = fileIds.isEmpty() ? Map.of() : mediaServiceClient.getFiles(fileIds).getData().stream()
                .filter(file -> file.url() != null).collect(Collectors.toMap(com.ecm.catalog.dto.response.MediaFileResponse::id,
                        com.ecm.catalog.dto.response.MediaFileResponse::url));
        Map<UUID, List<ProductImage>> byVariant = images.stream().collect(Collectors.groupingBy(ProductImage::getProductVariantId));
        return variants.stream().map(v -> {
            String image = byVariant.getOrDefault(v.getId(), List.of()).stream()
                    .sorted((a, b) -> Boolean.compare(b.isMain(), a.isMain()))
                    .map(i -> urls.get(i.getFileId())).filter(java.util.Objects::nonNull).findFirst().orElse(null);
            Product product = products.get(v.getProductId());
            return new CartVariantDetailsResponse(v.getId(), v.getProductId(), product == null ? null : product.getName(),
                    v.getSku(), v.getModel(), v.getListPrice(), v.getQuantity(), v.getStatus().name(), image);
        }).toList();
    }
}
