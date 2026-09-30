package com.ecm.catalog.service;

import com.ecm.catalog.client.MediaServiceClient;
import com.ecm.catalog.dto.request.ProductFilterRequest;
import com.ecm.catalog.dto.response.*;
import com.ecm.catalog.entity.*;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.mapper.OptionMapper;
import com.ecm.catalog.mapper.ProductImageMapper;
import com.ecm.catalog.mapper.ProductMapper;
import com.ecm.catalog.mapper.ProductVariantMapper;
import com.ecm.catalog.repository.*;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int DEFAULT_LIMIT = 20;

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final VariantOptionRepository variantOptionRepository;
    private final OptionRepository optionRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final ProductVariantMapper productVariantMapper;
    private final ProductImageMapper productImageMapper;
    private final OptionMapper optionMapper;
    private final MediaServiceClient mediaServiceClient;

    @Transactional(readOnly = true)
    public CursorPageResponse<ProductSummaryResponse> getProducts(ProductFilterRequest filter) {
        // 1. Validate price range
        validatePriceRange(filter);

        // 2. Sanitize pagination limit and search keyword pattern
        int pageSize = (filter.getLimit() != null && filter.getLimit() > 0) ? filter.getLimit() : DEFAULT_LIMIT;
        Pageable pageable = PageRequest.of(0, pageSize + 1);
        String keywordPattern = (filter.getKeyword() != null && !filter.getKeyword().isBlank())
                ? "%" + filter.getKeyword().trim().toLowerCase() + "%"
                : null;

        // 3. Fetch products using keyset cursor pagination
        List<Product> products = (filter.getCursor() == null)
                ? productRepository.findInitial(CatalogStatus.ACTIVE, filter.getCategoryId(),
                        filter.getBrandId(), keywordPattern, filter.getMinPrice(), filter.getMaxPrice(), pageable)
                : productRepository.findAfterCursor(CatalogStatus.ACTIVE, filter.getCursor(),
                        filter.getCategoryId(), filter.getBrandId(), keywordPattern,
                        filter.getMinPrice(), filter.getMaxPrice(), pageable);

        // 4. Evaluate next cursor and truncate extra element
        boolean hasNext = products.size() > pageSize;
        List<Product> results = hasNext ? products.subList(0, pageSize) : products;
        String nextCursor = (hasNext && !results.isEmpty()) 
                ? results.get(results.size() - 1).getId().toString() 
                : null;

        // 5. Map entities to summary DTOs and populate price ranges
        List<ProductSummaryResponse> responseList = enrichProductSummaries(results);

        // 6. Assemble and return cursor page response
        return CursorPageResponse.<ProductSummaryResponse>builder()
                .items(responseList)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .size(responseList.size())
                .build();
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProductById(UUID id) {
        // 1. Fetch product by ID
        Product product = productRepository.findByIdAndStatus(id, CatalogStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        // 2. Build detailed response with variants
        return buildProductDetail(product);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProductBySeoName(String seoName) {
        // 1. Fetch product by SEO slug
        Product product = productRepository.findBySeoNameAndStatus(seoName, CatalogStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Product", seoName));

        // 2. Build detailed response with variants
        return buildProductDetail(product);
    }

    @Transactional(readOnly = true)
    public ProductVariantResponse getProductVariant(UUID productId, UUID variantId) {
        // 1. Fetch specific variant by ID and product ID
        ProductVariant variant = productVariantRepository.findByIdAndStatus(variantId, CatalogStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));

        // 2. Verify variant belongs to product
        if (!variant.getProductId().equals(productId)) {
            throw new ResourceNotFoundException("ProductVariant", variantId);
        }

        // 3. Enrich variant with images and options
        return enrichVariant(variant);
    }

    /**
     * Enrich product summaries with brand/category names, price ranges, and main images.
     */
    private List<ProductSummaryResponse> enrichProductSummaries(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }

        // 1. Collect all product IDs
        List<UUID> productIds = products.stream().map(Product::getId).toList();

        // 2. Fetch active variants for the whole page
        List<ProductVariant> allVariants = productVariantRepository
                .findByProductIdInAndStatus(productIds, CatalogStatus.ACTIVE);

        // 3. Group variants by product ID
        Map<UUID, List<ProductVariant>> variantsByProduct = allVariants.stream()
                .collect(Collectors.groupingBy(ProductVariant::getProductId));

        // 4. Fetch main images for variants in one query
        List<UUID> variantIds = allVariants.stream().map(ProductVariant::getId).toList();
        Map<UUID, ProductImage> mainImages = variantIds.isEmpty() ? Map.of() :
                productImageRepository.findByProductVariantIdInAndStatus(variantIds, CatalogStatus.ACTIVE)
                        .stream()
                        .filter(ProductImage::isMain)
                        .collect(Collectors.toMap(ProductImage::getProductVariantId, img -> img, (a, b) -> a));
        Map<UUID, String> imageUrls = resolveImageUrls(mainImages.values().stream()
                .map(ProductImage::getFileId).toList());

        // 5. Fetch brand and category names
        Map<UUID, String> brandNames = fetchBrandNames(products);
        Map<UUID, String> categoryNames = fetchCategoryNames(products);

        // 6. Map products to summaries and enrich
        return products.stream()
                .filter(p -> variantsByProduct.containsKey(p.getId()))
                .map(product -> {
                    ProductSummaryResponse summary = productMapper.toSummaryResponse(product);
                    summary.setBrandName(brandNames.get(product.getBrandId()));
                    summary.setCategoryName(categoryNames.get(product.getCategoryId()));

                    List<ProductVariant> variants = variantsByProduct.get(product.getId());
                    if (variants != null && !variants.isEmpty()) {
                        long minPrice = variants.stream()
                                .map(ProductVariant::getListPrice)
                                .min(Long::compareTo)
                                .orElse(0L);
                        long maxPrice = variants.stream()
                                .map(ProductVariant::getListPrice)
                                .max(Long::compareTo)
                                .orElse(0L);
                        summary.setMinPrice(minPrice);
                        summary.setMaxPrice(maxPrice);

                        // Set main image from first variant's main image
                        ProductImage mainImage = mainImages.get(variants.get(0).getId());
                        if (mainImage != null) {
                            summary.setMainImageUrl(imageUrls.get(mainImage.getFileId()));
                        }
                    }

                    return summary;
                })
                .toList();
    }

    /**
     * Build detailed product response with all variants, images, and options.
     */
    private ProductDetailResponse buildProductDetail(Product product) {
        // 1. Map product to response
        ProductDetailResponse response = productMapper.toDetailResponse(product);

        // 2. Fetch brand and category names
        if (product.getBrandId() != null) {
            brandRepository.findByIdAndStatus(product.getBrandId(), CatalogStatus.ACTIVE)
                    .ifPresent(brand -> response.setBrandName(brand.getName()));
        }
        categoryRepository.findByIdAndStatus(product.getCategoryId(), CatalogStatus.ACTIVE)
                .ifPresent(category -> response.setCategoryName(category.getName()));

        // 3. Fetch all active variants with their images and options
        List<ProductVariant> variants = productVariantRepository
                .findByProductIdAndStatus(product.getId(), CatalogStatus.ACTIVE);
        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();
        Map<UUID, List<ProductImage>> imagesByVariant = variantIds.isEmpty() ? Map.of()
                : productImageRepository.findByProductVariantIdInAndStatus(variantIds, CatalogStatus.ACTIVE)
                        .stream().collect(Collectors.groupingBy(ProductImage::getProductVariantId));
        Map<UUID, List<OptionResponse>> optionsByVariant = fetchOptionsByVariant(variantIds);
        Map<UUID, String> imageUrls = resolveImageUrls(imagesByVariant.values().stream()
                .flatMap(Collection::stream)
                .map(ProductImage::getFileId).toList());

        List<ProductVariantResponse> variantResponses = variants.stream()
                .map(variant -> {
                    ProductVariantResponse variantResponse = productVariantMapper.toResponse(variant);
                    List<ProductImageResponse> images = imagesByVariant
                            .getOrDefault(variant.getId(), List.of())
                            .stream()
                            .map(image -> {
                                ProductImageResponse imageResponse = productImageMapper.toResponse(image);
                                imageResponse.setUrl(imageUrls.get(image.getFileId()));
                                return imageResponse;
                            })
                            .toList();
                    variantResponse.setImages(images);
                    variantResponse.setOptions(optionsByVariant.getOrDefault(variant.getId(), List.of()));
                    return variantResponse;
                })
                .toList();
        response.setVariants(variantResponses);
        return response;
    }

    private ProductVariantResponse enrichVariant(ProductVariant variant) {
        ProductVariantResponse response = productVariantMapper.toResponse(variant);
        List<ProductImage> images = productImageRepository
                .findByProductVariantIdAndStatus(variant.getId(), CatalogStatus.ACTIVE);
        Map<UUID, String> imageUrls = resolveImageUrls(images.stream().map(ProductImage::getFileId).toList());
        response.setImages(images.stream()
                .map(image -> {
                    ProductImageResponse imageResponse = productImageMapper.toResponse(image);
                    imageResponse.setUrl(imageUrls.get(image.getFileId()));
                    return imageResponse;
                })
                .toList());
        response.setOptions(fetchOptionsByVariant(List.of(variant.getId()))
                .getOrDefault(variant.getId(), List.of()));
        return response;
    }

    private Map<UUID, String> resolveImageUrls(Collection<UUID> fileIds) {
        List<UUID> distinctFileIds = fileIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctFileIds.isEmpty()) {
            return Map.of();
        }
        return mediaServiceClient.getFiles(distinctFileIds).getData().stream()
                .filter(file -> file.url() != null)
                .collect(Collectors.toMap(MediaFileResponse::id, MediaFileResponse::url));
    }

    private Map<UUID, List<OptionResponse>> fetchOptionsByVariant(List<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<UUID>> optionIdsByVariant = variantOptionRepository
                .findByProductVariantIdInAndStatus(variantIds, CatalogStatus.ACTIVE).stream()
                .collect(Collectors.groupingBy(VariantOption::getProductVariantId,
                        Collectors.mapping(VariantOption::getOptionId, Collectors.toList())));
        List<UUID> optionIds = optionIdsByVariant.values().stream().flatMap(Collection::stream).distinct().toList();
        if (optionIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, OptionResponse> optionsById = optionRepository.findByIdInAndStatus(optionIds, CatalogStatus.ACTIVE)
                .stream().collect(Collectors.toMap(Option::getId, optionMapper::toResponse));
        return optionIdsByVariant.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream().map(optionsById::get).filter(Objects::nonNull).toList()));
    }

    private Map<UUID, String> fetchBrandNames(List<Product> products) {
        Set<UUID> brandIds = products.stream()
                .map(Product::getBrandId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        if (brandIds.isEmpty()) {
            return Map.of();
        }

        return brandRepository.findAllById(brandIds).stream()
                .filter(brand -> brand.getStatus() == CatalogStatus.ACTIVE)
                .collect(Collectors.toMap(Brand::getId, Brand::getName));
    }

    private Map<UUID, String> fetchCategoryNames(List<Product> products) {
        Set<UUID> categoryIds = products.stream()
                .map(Product::getCategoryId)
                .collect(Collectors.toSet());

        return categoryRepository.findAllById(categoryIds).stream()
                .filter(category -> category.getStatus() == CatalogStatus.ACTIVE)
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private void validatePriceRange(ProductFilterRequest filter) {
        Long minPrice = filter.getMinPrice();
        Long maxPrice = filter.getMaxPrice();
        if ((minPrice != null && minPrice < 0)
                || (maxPrice != null && maxPrice < 0)
                || (minPrice != null && maxPrice != null && minPrice > maxPrice)) {
            throw new BusinessException(CatalogErrorCode.INVALID_PRICE_RANGE);
        }
    }

    private String buildFileUrl(UUID fileId) {
        // TODO: integrate with media-service to get actual URL
        return "/api/v1/files/" + fileId;
    }
}
