package com.ecm.catalog.service;

import com.ecm.catalog.config.CatalogProperties;
import com.ecm.catalog.dto.response.StockSummaryResponse;
import com.ecm.catalog.dto.request.CreateVariantRequest;
import com.ecm.catalog.dto.request.UpdateVariantRequest;
import com.ecm.catalog.dto.response.ProductVariantResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.helper.MediaFileResolver;
import com.ecm.catalog.helper.OrderHistoryLookup;
import com.ecm.catalog.helper.ProductAssembler;
import com.ecm.catalog.helper.VariantOptionStore;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.catalog.util.BlankText;
import com.ecm.catalog.util.VariantOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes on one variant (UC-ADM-PROD-007 and its follow-ups) plus the by-id lookup other services call. Changing the
 * option groups of a product touches several variants at once and lives in {@link ProductVariationService}.
 */
@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final VariantOptionStore optionStore;
    private final OrderHistoryLookup orderHistory;
    private final MediaFileResolver mediaResolver;
    private final ProductAssembler assembler;
    private final CatalogProperties catalogProperties;

    @Transactional
    public ProductVariantResponse createVariant(UUID productId, CreateVariantRequest request, UUID employeeId) {
        // 1. The product must still exist (hidden products can get variants too)
        productRepository.findByIdAndStatusIn(productId, ProductService.ADMIN_VISIBLE_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        // 2. SKU and barcode must be free among non-deleted variants; the image and the options must be valid
        String sku = request.sku().trim();
        String barcode = BlankText.trimmedOrNull(request.barcode());
        if (productVariantRepository.existsBySkuAndStatusNot(sku, CatalogStatus.DELETED)
                || barcode != null && productVariantRepository.existsByBarcodeAndStatusNot(barcode, CatalogStatus.DELETED)) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT, "Variant SKU or barcode already exists");
        }
        VariantOptions.ensureUniqueNames(request.options());
        validateImage(request.imageFileId());

        // 3. The new ACTIVE variant must fit the option groups of the other ACTIVE variants
        VariantOptions.ensureFitsProduct(VariantOptions.combination(request.options()), optionStore.activeCombinations(productId, null));

        // 4. Save the variant and its option links in this transaction
        ProductVariant variant = productVariantRepository.save(ProductVariant.builder()
                .productId(productId).price(request.price()).quantity(request.quantity()).sku(sku)
                .model(request.model()).description(request.description()).warrantyMonths(request.warrantyMonths())
                .barcode(barcode).releaseAt(request.releaseAt()).imageFileId(request.imageFileId())
                .status(CatalogStatus.ACTIVE).createdBy(employeeId).build());
        optionStore.replace(variant.getId(), request.options());
        return assembler.variant(variant);
    }

    @Transactional
    public ProductVariantResponse updateVariant(UUID productId, UUID variantId, UpdateVariantRequest request, UUID employeeId) {
        // 1. Load the variant and check SKU and barcode against the other non-deleted variants
        ProductVariant variant = findLiveVariant(productId, variantId);
        String sku = request.sku().trim();
        String barcode = BlankText.trimmedOrNull(request.barcode());
        if (productVariantRepository.existsBySkuAndIdNotAndStatusNot(sku, variantId, CatalogStatus.DELETED)
                || barcode != null && productVariantRepository.existsByBarcodeAndIdNotAndStatusNot(barcode, variantId, CatalogStatus.DELETED)) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT, "Variant SKU or barcode already exists");
        }
        VariantOptions.ensureUniqueNames(request.options());
        validateImage(request.imageFileId());

        // 2. The options must still fit the product, and an ordered variant keeps the values it was sold with
        Map<String, String> combination = VariantOptions.combination(request.options());
        if (variant.getStatus() == CatalogStatus.ACTIVE) {
            VariantOptions.ensureFitsProduct(combination, optionStore.activeCombinations(productId, variantId));
        }
        Map<String, String> before = optionStore.combinations(List.of(variantId)).get(variantId);
        if (VariantOptions.changesValues(before, combination) && orderHistory.hasOrders(variantId)) {
            throw new BusinessException(CatalogErrorCode.VARIANT_OPTIONS_LOCKED);
        }

        // 3. Apply the edit (stock is left alone, see adjustStock) and replace the options
        variant.setPrice(request.price());
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
        optionStore.replace(variantId, request.options());
        return assembler.variant(variant);
    }

    @Transactional
    public ProductVariantResponse updateVariantStatus(UUID productId, UUID variantId, CatalogStatus status, UUID employeeId) {
        // 1. Only show/hide is allowed here; removal has its own rules in deleteVariant
        if (status == CatalogStatus.DELETED) {
            throw new BusinessException(CatalogErrorCode.INVALID_PRODUCT_STATUS);
        }

        // 2. A variant shown again must fit the option groups the other ACTIVE variants have now
        ProductVariant variant = findLiveVariant(productId, variantId);
        if (status == CatalogStatus.ACTIVE && variant.getStatus() != CatalogStatus.ACTIVE) {
            VariantOptions.ensureFitsProduct(optionStore.combinations(List.of(variantId)).get(variantId),
                    optionStore.activeCombinations(productId, variantId));
        }

        // 3. Apply
        variant.setStatus(status);
        variant.setUpdatedAt(Instant.now());
        variant.setUpdatedBy(employeeId);
        return assembler.variant(productVariantRepository.save(variant));
    }

    /** Adds or takes off stock (a delivery, a count correction) without touching what orders have reserved meanwhile. */
    @Transactional
    public ProductVariantResponse adjustStock(UUID productId, UUID variantId, int delta, UUID employeeId) {
        findLiveVariant(productId, variantId);
        if (productVariantRepository.adjustQuantity(variantId, delta, Instant.now(), employeeId) == 0) {
            throw new BusinessException(CatalogErrorCode.STOCK_BELOW_ZERO);
        }
        return assembler.variant(findLiveVariant(productId, variantId));
    }

    @Transactional
    public void deleteVariant(UUID productId, UUID variantId, UUID employeeId) {
        // 1. A variant that has been ordered or holds stock is kept (set it INACTIVE instead)
        ProductVariant variant = findLiveVariant(productId, variantId);
        if (variant.getQuantity() > 0 || orderHistory.hasOrders(variantId)) {
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

    /** How many active variants are running low and how many are sold out, for the dashboard (UC-ADM-DASH-001). */
    @Transactional(readOnly = true)
    public StockSummaryResponse getStockSummary() {
        int threshold = catalogProperties.getLowStockThreshold();
        return new StockSummaryResponse(
                productVariantRepository.countByStatusAndQuantityBetween(CatalogStatus.ACTIVE, 1, threshold),
                productVariantRepository.countByStatusAndQuantityBetween(CatalogStatus.ACTIVE, 0, 0),
                threshold);
    }

    private ProductVariant findLiveVariant(UUID productId, UUID variantId) {
        return productVariantRepository.findById(variantId)
                .filter(variant -> variant.getProductId().equals(productId) && variant.getStatus() != CatalogStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));
    }

    private void validateImage(UUID imageFileId) {
        if (imageFileId != null) {
            mediaResolver.requireExisting(List.of(imageFileId));
        }
    }
}
