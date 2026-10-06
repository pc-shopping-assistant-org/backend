package com.ecm.catalog.service;

import com.ecm.catalog.client.OrderServiceClient;
import com.ecm.catalog.dto.request.CreateProductRequest;
import com.ecm.catalog.dto.request.ProductImageRequest;
import com.ecm.catalog.dto.request.UpdateProductRequest;
import com.ecm.catalog.dto.response.ProductDetailResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Product;
import com.ecm.catalog.entity.ProductImage;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.repository.BrandRepository;
import com.ecm.catalog.repository.CategoryRepository;
import com.ecm.catalog.repository.ProductImageRepository;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Admin-side product writes: create, edit, hide/show and delete. Variants live in {@link ProductVariantService}. */
@Service
@RequiredArgsConstructor
public class ProductService {

    /** Statuses of products and variants the admin can see; soft-deleted rows stay hidden. */
    static final List<CatalogStatus> ADMIN_VISIBLE_STATUSES = List.of(CatalogStatus.ACTIVE, CatalogStatus.INACTIVE);

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final OrderServiceClient orderServiceClient;
    private final ProductSpecificationValidator specificationValidator;
    private final ProductMediaResolver mediaResolver;
    private final ProductAssembler assembler;

    @Transactional
    public ProductDetailResponse createProduct(CreateProductRequest request, UUID employeeId) {
        // 1. Category and brand must be usable; the SEO name must be free among non-deleted products
        validateReferences(request.categoryId(), request.brandId());
        String seoName = normalizeSeo(request.seoName(), request.name());
        if (productRepository.existsBySeoNameAndStatusNot(seoName, CatalogStatus.DELETED)) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT, "SEO name already exists");
        }

        // 2. Specifications must satisfy the attribute template of the category
        var specifications = specificationValidator.validate(request.categoryId(), request.specifications());

        // 3. The gallery must be well formed and its files must exist
        validateGallery(request.images());

        // 4. Persist the product and its gallery
        Product product = productRepository.save(Product.builder()
                .name(request.name().trim()).seoName(seoName).brandId(request.brandId()).categoryId(request.categoryId())
                .specifications(specifications).description(request.description())
                .status(CatalogStatus.ACTIVE).createdBy(employeeId).build());
        saveGallery(product.getId(), request.images());
        return assembler.detail(product, ADMIN_VISIBLE_STATUSES);
    }

    @Transactional
    public ProductDetailResponse updateProduct(UUID id, UpdateProductRequest request, UUID employeeId) {
        // 1. Load the product and check the references and SEO name
        Product product = findLiveProduct(id);
        validateReferences(request.categoryId(), request.brandId());
        String seoName = normalizeSeo(request.seoName(), request.name());
        if (productRepository.existsBySeoNameAndIdNotAndStatusNot(seoName, id, CatalogStatus.DELETED)) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT, "SEO name already exists");
        }

        // 2. Specifications are checked against the (possibly new) category
        var specifications = specificationValidator.validate(request.categoryId(), request.specifications());

        // 3. A supplied gallery replaces the current one
        if (request.images() != null) {
            validateGallery(request.images());
        }

        // 4. Apply the edit
        product.setName(request.name().trim());
        product.setSeoName(seoName);
        product.setBrandId(request.brandId());
        product.setCategoryId(request.categoryId());
        product.setSpecifications(specifications);
        product.setDescription(request.description());
        product.setUpdatedAt(Instant.now());
        product.setUpdatedBy(employeeId);
        productRepository.save(product);
        if (request.images() != null) {
            productImageRepository.deleteByProductId(id);
            saveGallery(id, request.images());
        }
        return assembler.detail(product, ADMIN_VISIBLE_STATUSES);
    }

    @Transactional
    public ProductDetailResponse updateProductStatus(UUID id, CatalogStatus status, UUID employeeId) {
        // 1. Only show/hide is allowed here; removal has its own rules in deleteProduct
        if (status == CatalogStatus.DELETED) {
            throw new BusinessException(CatalogErrorCode.INVALID_PRODUCT_STATUS);
        }

        // 2. Apply
        Product product = findLiveProduct(id);
        product.setStatus(status);
        product.setUpdatedAt(Instant.now());
        product.setUpdatedBy(employeeId);
        return assembler.detail(productRepository.save(product), ADMIN_VISIBLE_STATUSES);
    }

    @Transactional
    public void deleteProduct(UUID id, UUID employeeId) {
        // 1. A product that already received stock or was ordered can no longer be deleted (hide it instead)
        Product product = findLiveProduct(id);
        List<ProductVariant> variants = productVariantRepository.findByProductIdAndStatusNot(id, CatalogStatus.DELETED);
        for (ProductVariant variant : variants) {
            if (variant.getQuantity() > 0 || Boolean.TRUE.equals(orderServiceClient.hasOrderHistory(variant.getId()).getData())) {
                throw new BusinessException(CatalogErrorCode.PRODUCT_IN_USE);
            }
        }

        // 2. Soft delete the product with its variants so their SKU, barcode and SEO name can be reused
        Instant now = Instant.now();
        product.setStatus(CatalogStatus.DELETED);
        product.setUpdatedAt(now);
        product.setUpdatedBy(employeeId);
        productRepository.save(product);
        for (ProductVariant variant : variants) {
            variant.setStatus(CatalogStatus.DELETED);
            variant.setUpdatedAt(now);
            variant.setUpdatedBy(employeeId);
        }
        productVariantRepository.saveAll(variants);
    }

    private Product findLiveProduct(UUID id) {
        return productRepository.findByIdAndStatusIn(id, ADMIN_VISIBLE_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private void validateReferences(UUID categoryId, UUID brandId) {
        if (!categoryRepository.existsByIdAndStatus(categoryId, CatalogStatus.ACTIVE)) {
            throw new BusinessException(CatalogErrorCode.INVALID_CATALOG_REFERENCE, "Category must exist and be ACTIVE");
        }
        if (brandId != null && !brandRepository.existsByIdAndStatus(brandId, CatalogStatus.ACTIVE)) {
            throw new BusinessException(CatalogErrorCode.INVALID_CATALOG_REFERENCE, "Brand must exist and be ACTIVE");
        }
    }

    private void validateGallery(List<ProductImageRequest> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        if (images.stream().filter(ProductImageRequest::main).count() > 1) {
            throw new BusinessException(CatalogErrorCode.INVALID_GALLERY, "A product can have only one main image");
        }
        Set<UUID> fileIds = new HashSet<>();
        for (ProductImageRequest image : images) {
            if (!fileIds.add(image.fileId())) {
                throw new BusinessException(CatalogErrorCode.INVALID_GALLERY, "Image listed more than once: " + image.fileId());
            }
        }
        mediaResolver.requireExisting(fileIds);
    }

    private void saveGallery(UUID productId, List<ProductImageRequest> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        productImageRepository.saveAll(images.stream().map(image -> ProductImage.builder()
                .productId(productId).imageFileId(image.fileId()).isMain(image.main()).build()).toList());
    }

    private String normalizeSeo(String seoName, String name) {
        String value = (seoName == null || seoName.isBlank() ? name : seoName).trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (value.isBlank()) {
            throw new BusinessException(CatalogErrorCode.INVALID_CATALOG_REFERENCE, "SEO name is invalid");
        }
        return value;
    }
}
