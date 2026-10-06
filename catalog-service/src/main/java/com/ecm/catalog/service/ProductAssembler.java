package com.ecm.catalog.service;

import com.ecm.catalog.dto.response.OptionResponse;
import com.ecm.catalog.dto.response.ProductDetailResponse;
import com.ecm.catalog.dto.response.ProductImageResponse;
import com.ecm.catalog.dto.response.ProductSummaryResponse;
import com.ecm.catalog.dto.response.ProductVariantResponse;
import com.ecm.catalog.entity.Brand;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Category;
import com.ecm.catalog.entity.Option;
import com.ecm.catalog.entity.Product;
import com.ecm.catalog.entity.ProductImage;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.entity.VariantOption;
import com.ecm.catalog.mapper.OptionMapper;
import com.ecm.catalog.mapper.ProductMapper;
import com.ecm.catalog.mapper.ProductVariantMapper;
import com.ecm.catalog.repository.BrandRepository;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.catalog.repository.OptionRepository;
import com.ecm.catalog.repository.ProductImageRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.catalog.repository.VariantOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds product, variant and summary responses, loading brands, categories, gallery images, variants and options
 * in batches (a fixed number of queries per call, whatever the number of products) and resolving image URLs with a
 * single Media Service call.
 */
@Component
@RequiredArgsConstructor
public class ProductAssembler {

    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVariantRepository productVariantRepository;
    private final VariantOptionRepository variantOptionRepository;
    private final OptionRepository optionRepository;
    private final ProductMapper productMapper;
    private final ProductVariantMapper productVariantMapper;
    private final OptionMapper optionMapper;
    private final ProductMediaResolver mediaResolver;

    public ProductDetailResponse detail(Product product, Collection<CatalogStatus> variantStatuses) {
        // 1. Load everything the response needs
        List<ProductImage> gallery = productImageRepository.findByProductIdIn(List.of(product.getId()));
        List<ProductVariant> variants = productVariantRepository.findByProductIdAndStatusIn(product.getId(), variantStatuses);
        Map<UUID, List<OptionResponse>> optionsByVariant = optionsByVariant(variants);

        // 2. One Media Service call for the gallery and the variant images together
        Set<UUID> fileIds = new HashSet<>();
        gallery.forEach(image -> fileIds.add(image.getImageFileId()));
        variants.forEach(variant -> fileIds.add(variant.getImageFileId()));
        Map<UUID, String> urls = mediaResolver.resolveUrls(fileIds);

        // 3. Assemble
        ProductDetailResponse response = productMapper.toDetailResponse(product);
        response.setBrandName(product.getBrandId() == null ? null
                : brandRepository.findById(product.getBrandId()).map(Brand::getName).orElse(null));
        response.setCategoryName(categoryRepository.findById(product.getCategoryId()).map(Category::getName).orElse(null));
        response.setImages(gallery.stream().map(image -> toImageResponse(image, urls)).toList());
        response.setVariants(variants.stream().map(variant -> toVariantResponse(variant, optionsByVariant, urls)).toList());
        return response;
    }

    public List<ProductVariantResponse> variants(List<ProductVariant> variants) {
        if (variants.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<OptionResponse>> optionsByVariant = optionsByVariant(variants);
        Map<UUID, String> urls = mediaResolver.resolveUrls(variants.stream().map(ProductVariant::getImageFileId).toList());
        return variants.stream().map(variant -> toVariantResponse(variant, optionsByVariant, urls)).toList();
    }

    public ProductVariantResponse variant(ProductVariant variant) {
        return variants(List.of(variant)).get(0);
    }

    /**
     * Summaries with brand and category names, the price range of the variants whose status is in
     * {@code variantStatuses}, and the main gallery image. With {@code onlyWithVariants} products that have no such
     * variant are left out (the public storefront does not list products that cannot be bought).
     */
    public List<ProductSummaryResponse> summaries(List<Product> products, Collection<CatalogStatus> variantStatuses,
                                                  boolean onlyWithVariants) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<UUID> productIds = products.stream().map(Product::getId).toList();

        Map<UUID, List<ProductVariant>> variantsByProduct = productVariantRepository
                .findByProductIdInAndStatusIn(productIds, variantStatuses).stream()
                .collect(Collectors.groupingBy(ProductVariant::getProductId));
        Map<UUID, ProductImage> mainImages = productImageRepository.findMainByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(ProductImage::getProductId, Function.identity()));
        Map<UUID, String> urls = mediaResolver.resolveUrls(mainImages.values().stream().map(ProductImage::getImageFileId).toList());
        Map<UUID, String> brandNames = brandNames(products);
        Map<UUID, String> categoryNames = categoryNames(products);

        return products.stream()
                .filter(product -> !onlyWithVariants || variantsByProduct.containsKey(product.getId()))
                .map(product -> {
                    ProductSummaryResponse summary = productMapper.toSummaryResponse(product);
                    summary.setBrandName(lookup(brandNames, product.getBrandId()));
                    summary.setCategoryName(lookup(categoryNames, product.getCategoryId()));
                    List<ProductVariant> variants = variantsByProduct.getOrDefault(product.getId(), List.of());
                    summary.setMinPrice(variants.stream().map(ProductVariant::getPrice).min(Long::compareTo).orElse(null));
                    summary.setMaxPrice(variants.stream().map(ProductVariant::getPrice).max(Long::compareTo).orElse(null));
                    ProductImage main = mainImages.get(product.getId());
                    summary.setMainImageUrl(main == null ? null : lookup(urls, main.getImageFileId()));
                    return summary;
                })
                .toList();
    }

    // Immutable maps (Map.of, Collectors.toMap into one) reject a null key, and brand and image ids are optional.
    private static <V> V lookup(Map<UUID, V> map, UUID key) {
        return key == null ? null : map.get(key);
    }

    private ProductImageResponse toImageResponse(ProductImage image, Map<UUID, String> urls) {
        return new ProductImageResponse(image.getId(), image.getImageFileId(), lookup(urls, image.getImageFileId()), image.isMain());
    }

    private ProductVariantResponse toVariantResponse(ProductVariant variant, Map<UUID, List<OptionResponse>> optionsByVariant,
                                                     Map<UUID, String> urls) {
        ProductVariantResponse response = productVariantMapper.toResponse(variant);
        response.setImageUrl(lookup(urls, variant.getImageFileId()));
        response.setOptions(optionsByVariant.getOrDefault(variant.getId(), List.of()));
        return response;
    }

    private Map<UUID, List<OptionResponse>> optionsByVariant(List<ProductVariant> variants) {
        if (variants.isEmpty()) {
            return Map.of();
        }
        List<VariantOption> links = variantOptionRepository.findByProductVariantIdIn(variants.stream().map(ProductVariant::getId).toList());
        Map<UUID, OptionResponse> optionsById = optionRepository
                .findAllById(links.stream().map(VariantOption::getOptionId).distinct().toList()).stream()
                .collect(Collectors.toMap(Option::getId, optionMapper::toResponse));
        return links.stream().collect(Collectors.groupingBy(VariantOption::getProductVariantId,
                Collectors.mapping(link -> optionsById.get(link.getOptionId()),
                        Collectors.filtering(Objects::nonNull, Collectors.toList()))));
    }

    private Map<UUID, String> brandNames(List<Product> products) {
        Set<UUID> brandIds = products.stream().map(Product::getBrandId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (brandIds.isEmpty()) {
            return Map.of();
        }
        return brandRepository.findAllById(brandIds).stream().collect(Collectors.toMap(Brand::getId, Brand::getName));
    }

    private Map<UUID, String> categoryNames(List<Product> products) {
        Set<UUID> categoryIds = products.stream().map(Product::getCategoryId).collect(Collectors.toSet());
        return categoryRepository.findAllById(categoryIds).stream().collect(Collectors.toMap(Category::getId, Category::getName));
    }
}
