package com.ecm.catalog.service;

import com.ecm.catalog.dto.request.SaveVariationsRequest;
import com.ecm.catalog.dto.request.VariationItemRequest;
import com.ecm.catalog.dto.response.ProductDetailResponse;
import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Product;
import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.exception.CatalogErrorCode;
import com.ecm.catalog.helper.MediaFileResolver;
import com.ecm.catalog.helper.OrderHistoryLookup;
import com.ecm.catalog.helper.ProductAssembler;
import com.ecm.catalog.helper.VariantOptionStore;
import com.ecm.catalog.mapper.ProductVariantMapper;
import com.ecm.catalog.repository.ProductRepository;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.catalog.util.BlankText;
import com.ecm.catalog.util.VariantOptions;
import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Replaces the variations of a product in one go, as the seller's "variations" form does: a product without option
 * groups can gain some (its variant becomes the first combination and keeps its stock and orders), and a product with
 * groups can drop them back to one variant.
 */
@Service
@RequiredArgsConstructor
public class ProductVariationService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final VariantOptionStore optionStore;
    private final OrderHistoryLookup orderHistory;
    private final MediaFileResolver mediaResolver;
    private final ProductAssembler assembler;
    private final ProductVariantMapper variantMapper;

    /**
     * The listed variants become the ACTIVE ones. An ACTIVE variant left out is deleted when it has no stock and no
     * orders, and hidden otherwise; an INACTIVE one left out stays as it is.
     */
    @Transactional
    public ProductDetailResponse saveVariations(UUID productId, SaveVariationsRequest request, UUID employeeId) {
        // 1. Lock the variants of the product so no order takes stock while they are rewritten
        Product product = productRepository.findByIdAndStatusIn(productId, ProductService.ADMIN_VISIBLE_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        Map<UUID, ProductVariant> current = productVariantRepository.findByProductIdForUpdate(productId, CatalogStatus.DELETED).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        List<VariationItemRequest> items = request.variants();

        // 2. The list must name each variant of this product at most once, share one shape of options, and use free codes
        ensureKnownIds(items, current);
        items.forEach(item -> VariantOptions.ensureUniqueNames(item.options()));
        List<Map<String, String>> combinations = items.stream().map(item -> VariantOptions.combination(item.options())).toList();
        VariantOptions.ensureConsistent(combinations);
        ensureCodesFree(items);
        List<UUID> images = items.stream().map(VariationItemRequest::imageFileId).filter(Objects::nonNull).distinct().toList();
        if (!images.isEmpty()) {
            mediaResolver.requireExisting(images);
        }

        // 3. An ordered variant keeps its option values, and the variants left out need to know whether they were ordered
        Set<UUID> kept = items.stream().map(VariationItemRequest::id).filter(Objects::nonNull).collect(Collectors.toSet());
        List<ProductVariant> leftOut = current.values().stream()
                .filter(variant -> variant.getStatus() == CatalogStatus.ACTIVE && !kept.contains(variant.getId())).toList();
        Set<UUID> ordered = orderedAmongTouched(items, combinations, leftOut);

        // 4. Write the variants, then their options (replacing options clears the persistence context)
        Instant now = Instant.now();
        List<UUID> savedIds = new ArrayList<>();
        for (VariationItemRequest item : items) {
            savedIds.add(productVariantRepository.save(apply(item, current.get(item.id()), productId, now, employeeId)).getId());
        }
        leftOut.forEach(variant -> variant.retire(ordered.contains(variant.getId()), now, employeeId));
        productVariantRepository.saveAll(leftOut);
        productVariantRepository.flush();
        for (int i = 0; i < items.size(); i++) {
            optionStore.replace(savedIds.get(i), items.get(i).options());
        }
        ProductDetailResponse detail = assembler.detail(product, ProductService.ADMIN_VISIBLE_STATUSES);
        orderHistory.markOrdered(detail.getVariants());
        return detail;
    }

    private static void ensureKnownIds(List<VariationItemRequest> items, Map<UUID, ProductVariant> current) {
        Set<UUID> seen = new HashSet<>();
        for (VariationItemRequest item : items) {
            if (item.id() == null) {
                continue;
            }
            if (!current.containsKey(item.id())) {
                throw new ResourceNotFoundException("ProductVariant", item.id());
            }
            if (!seen.add(item.id())) {
                throw new BusinessException(CatalogErrorCode.VARIANT_LISTED_TWICE, "Variant listed more than once: " + item.id());
            }
        }
    }

    /**
     * Each SKU and barcode may be used once in the list, and not by any other non-deleted variant, including one this
     * save hides or deletes (codes are not handed from one variant to another in the same save).
     */
    private void ensureCodesFree(List<VariationItemRequest> items) {
        Map<String, UUID> skuOwners = new HashMap<>();
        Map<String, UUID> barcodeOwners = new HashMap<>();
        for (VariationItemRequest item : items) {
            String barcode = BlankText.trimmedOrNull(item.barcode());
            if (skuOwners.containsKey(item.sku().trim()) || barcode != null && barcodeOwners.containsKey(barcode)) {
                throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT, "Variant SKU or barcode listed more than once");
            }
            skuOwners.put(item.sku().trim(), item.id());
            if (barcode != null) {
                barcodeOwners.put(barcode, item.id());
            }
        }
        boolean taken = productVariantRepository.findBySkuInAndStatusNot(skuOwners.keySet(), CatalogStatus.DELETED).stream()
                .anyMatch(variant -> !variant.getId().equals(skuOwners.get(variant.getSku())))
                || !barcodeOwners.isEmpty() && productVariantRepository.findByBarcodeInAndStatusNot(barcodeOwners.keySet(), CatalogStatus.DELETED)
                .stream().anyMatch(variant -> !variant.getId().equals(barcodeOwners.get(variant.getBarcode())));
        if (taken) {
            throw new BusinessException(CatalogErrorCode.RESOURCE_CONFLICT, "Variant SKU or barcode already exists");
        }
    }

    /**
     * Asks the Order Service once about the listed variants whose option values change and the variants left out, and
     * returns those that were ordered; rejects the save when a variant changing its values is among them.
     */
    private Set<UUID> orderedAmongTouched(List<VariationItemRequest> items, List<Map<String, String>> combinations,
                                          List<ProductVariant> leftOut) {
        Map<UUID, Map<String, String>> before = optionStore.combinations(
                items.stream().map(VariationItemRequest::id).filter(Objects::nonNull).toList());
        List<UUID> recombined = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            UUID id = items.get(i).id();
            if (id != null && VariantOptions.changesValues(before.get(id), combinations.get(i))) {
                recombined.add(id);
            }
        }
        Set<UUID> ordered = orderHistory.orderedAmong(Stream.concat(recombined.stream(), leftOut.stream().map(ProductVariant::getId)).toList());
        if (recombined.stream().anyMatch(ordered::contains)) {
            throw new BusinessException(CatalogErrorCode.VARIANT_OPTIONS_LOCKED);
        }
        return ordered;
    }

    /** The listed variant: an existing one edited and made ACTIVE (its stock untouched), or a new one with its opening stock. */
    private ProductVariant apply(VariationItemRequest item, ProductVariant existing, UUID productId, Instant now, UUID employeeId) {
        ProductVariant variant = existing != null ? existing : ProductVariant.builder()
                .productId(productId).quantity(item.quantity() == null ? 0 : item.quantity()).createdBy(employeeId).build();
        variantMapper.updateEntity(item, variant);
        variant.setStatus(CatalogStatus.ACTIVE);
        if (existing != null) {
            variant.setUpdatedAt(now);
            variant.setUpdatedBy(employeeId);
        }
        return variant;
    }
}
