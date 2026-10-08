package com.ecm.catalog.service;

import com.ecm.catalog.config.CatalogProperties;
import com.ecm.catalog.dto.response.StockSummaryResponse;
import com.ecm.catalog.client.OrderServiceClient;
import com.ecm.catalog.dto.request.CreateVariantRequest;
import com.ecm.catalog.dto.request.UpdateVariantRequest;
import com.ecm.catalog.dto.request.VariantOptionRequest;
import com.ecm.catalog.dto.response.ProductVariantResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Option;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.entity.VariantOption;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.repository.OptionRepository;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.catalog.repository.VariantOptionRepository;
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

/** Variant writes (UC-ADM-PROD-007 and its follow-ups) plus the by-id lookup other services call. */
@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OptionRepository optionRepository;
    private final VariantOptionRepository variantOptionRepository;
    private final OrderServiceClient orderServiceClient;
    private final ProductMediaResolver mediaResolver;
    private final ProductAssembler assembler;
    private final CatalogProperties catalogProperties;

    @Transactional
    public ProductVariantResponse createVariant(UUID productId, CreateVariantRequest request, UUID employeeId) {
        // 1. The product must still exist (hidden products can get variants too)
        productRepository.findByIdAndStatusIn(productId, ProductService.ADMIN_VISIBLE_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        // 2. SKU and barcode must be free among non-deleted variants; the image and the options must be valid
        String sku = request.sku().trim();
        String barcode = normalizeBarcode(request.barcode());
        if (productVariantRepository.existsBySkuAndStatusNot(sku, CatalogStatus.DELETED)
                || barcode != null && productVariantRepository.existsByBarcodeAndStatusNot(barcode, CatalogStatus.DELETED)) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT, "Variant SKU or barcode already exists");
        }
        validateOptionNames(request.options());
        validateImage(request.imageFileId());

        // 3. Save the variant and its option links in this transaction
        ProductVariant variant = productVariantRepository.save(ProductVariant.builder()
                .productId(productId).price(request.price()).quantity(request.quantity()).sku(sku)
                .model(request.model()).description(request.description()).warrantyMonths(request.warrantyMonths())
                .barcode(barcode).releaseAt(request.releaseAt()).imageFileId(request.imageFileId())
                .status(CatalogStatus.ACTIVE).createdBy(employeeId).build());
        saveOptions(variant.getId(), request.options());
        return assembler.variant(variant);
    }

    @Transactional
    public ProductVariantResponse updateVariant(UUID productId, UUID variantId, UpdateVariantRequest request, UUID employeeId) {
        // 1. Load the variant and check SKU and barcode against the other non-deleted variants
        ProductVariant variant = findLiveVariant(productId, variantId);
        String sku = request.sku().trim();
        String barcode = normalizeBarcode(request.barcode());
        if (productVariantRepository.existsBySkuAndIdNotAndStatusNot(sku, variantId, CatalogStatus.DELETED)
                || barcode != null && productVariantRepository.existsByBarcodeAndIdNotAndStatusNot(barcode, variantId, CatalogStatus.DELETED)) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT, "Variant SKU or barcode already exists");
        }
        validateOptionNames(request.options());
        validateImage(request.imageFileId());

        // 2. Apply the edit and replace the options
        variant.setPrice(request.price());
        variant.setQuantity(request.quantity());
        variant.setSku(sku);
        variant.setModel(request.model());
        variant.setDescription(request.description());
        variant.setWarrantyMonths(request.warrantyMonths());
        variant.setBarcode(barcode);
        variant.setReleaseAt(request.releaseAt());
        variant.setImageFileId(request.imageFileId());
        variant.setUpdatedAt(Instant.now());
        variant.setUpdatedBy(employeeId);
        productVariantRepository.save(variant);
        variantOptionRepository.deleteByProductVariantId(variantId);
        saveOptions(variantId, request.options());
        return assembler.variant(variant);
    }

    @Transactional
    public ProductVariantResponse updateVariantStatus(UUID productId, UUID variantId, CatalogStatus status, UUID employeeId) {
        if (status == CatalogStatus.DELETED) {
            throw new BusinessException(CatalogErrorCode.INVALID_PRODUCT_STATUS);
        }
        ProductVariant variant = findLiveVariant(productId, variantId);
        variant.setStatus(status);
        variant.setUpdatedAt(Instant.now());
        variant.setUpdatedBy(employeeId);
        return assembler.variant(productVariantRepository.save(variant));
    }

    @Transactional
    public void deleteVariant(UUID productId, UUID variantId, UUID employeeId) {
        // 1. A variant that has been ordered or holds stock is kept (set it INACTIVE instead)
        ProductVariant variant = findLiveVariant(productId, variantId);
        if (variant.getQuantity() > 0 || Boolean.TRUE.equals(orderServiceClient.hasOrderHistory(variantId).getData())) {
            throw new BusinessException(CatalogErrorCode.PRODUCT_IN_USE);
        }

        // 2. Soft delete so the SKU and barcode can be reused
        variant.setStatus(CatalogStatus.DELETED);
        variant.setUpdatedAt(Instant.now());
        variant.setUpdatedBy(employeeId);
        productVariantRepository.save(variant);
    }

    @Transactional(readOnly = true)
    public ProductVariantResponse getById(UUID id) {
        return assembler.variant(productVariantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", id)));
    }

    private ProductVariant findLiveVariant(UUID productId, UUID variantId) {
        return productVariantRepository.findById(variantId)
                .filter(variant -> variant.getProductId().equals(productId) && variant.getStatus() != CatalogStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));
    }

    private String normalizeBarcode(String barcode) {
        return barcode == null || barcode.isBlank() ? null : barcode.trim();
    }

    private void validateImage(UUID imageFileId) {
        if (imageFileId != null) {
            mediaResolver.requireExisting(List.of(imageFileId));
        }
    }

    // A variant may hold only one option per option name, compared ignoring case and surrounding whitespace.
    private void validateOptionNames(List<VariantOptionRequest> options) {
        if (options == null) {
            return;
        }
        Set<String> names = new HashSet<>();
        for (VariantOptionRequest option : options) {
            if (!names.add(option.name().trim().toUpperCase(Locale.ROOT))) {
                throw new BusinessException(CatalogErrorCode.INVALID_VARIANT_OPTIONS,
                        "Variant has more than one option named " + option.name().trim());
            }
        }
    }

    private void saveOptions(UUID variantId, List<VariantOptionRequest> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        variantOptionRepository.saveAll(options.stream()
                .map(option -> VariantOption.builder().productVariantId(variantId).optionId(findOrCreateOption(option).getId()).build())
                .toList());
    }

    private Option findOrCreateOption(VariantOptionRequest request) {
        String name = request.name().trim();
        String value = request.value().trim();
        return optionRepository.findByNameIgnoreCaseAndValueIgnoreCase(name, value)
                .orElseGet(() -> optionRepository.save(Option.builder().name(name).value(value).build()));
    }

    /** How many active variants are running low and how many are sold out, for the dashboard (UC-ADM-DASH-001). */
    @Transactional(readOnly = true)
    public StockSummaryResponse getStockSummary() {
        int threshold = catalogProperties.getLowStockThreshold();
        return new StockSummaryResponse(
                productVariantRepository.countByStatusAndQuantityBetween(CatalogStatus.ACTIVE, 1, threshold),
                productVariantRepository.countByStatusAndQuantityBetween(CatalogStatus.ACTIVE, 0, 0),
                threshold);
    }
}
