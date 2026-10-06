package com.ecm.catalog.service;

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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartVariantDetailsService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ProductMediaResolver mediaResolver;

    @Transactional(readOnly = true)
    public List<CartVariantDetailsResponse> getDetails(List<UUID> ids) {
        // 1. Load the requested variants with their products and the main product images
        List<UUID> variantIds = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (variantIds.isEmpty()) {
            return List.of();
        }
        List<ProductVariant> variants = variantRepository.findAllById(variantIds);
        List<UUID> productIds = variants.stream().map(ProductVariant::getProductId).distinct().toList();
        Map<UUID, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<UUID, ProductImage> mainImages = imageRepository.findMainByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(ProductImage::getProductId, Function.identity()));

        // 2. A variant shows its own image, falling back to the main image of its product
        Map<UUID, String> urls = mediaResolver.resolveUrls(java.util.stream.Stream.concat(
                variants.stream().map(ProductVariant::getImageFileId),
                mainImages.values().stream().map(ProductImage::getImageFileId)).toList());
        return variants.stream().map(variant -> {
            ProductImage main = mainImages.get(variant.getProductId());
            String imageUrl = variant.getImageFileId() == null ? null : urls.get(variant.getImageFileId());
            if (imageUrl == null && main != null) {
                imageUrl = urls.get(main.getImageFileId());
            }
            Product product = products.get(variant.getProductId());
            boolean sellable = variant.getStatus() == CatalogStatus.ACTIVE && product != null && product.getStatus() == CatalogStatus.ACTIVE;
            return new CartVariantDetailsResponse(variant.getId(), variant.getProductId(),
                    product == null ? null : product.getName(), variant.getSku(), variant.getModel(), variant.getPrice(),
                    variant.getQuantity(), variant.getStatus().name(), imageUrl, sellable);
        }).toList();
    }
}
