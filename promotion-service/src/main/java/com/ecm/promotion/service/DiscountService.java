package com.ecm.promotion.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.DuplicateResourceException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.PageResponse;
import com.ecm.promotion.dto.request.ApplyDiscountRequest;
import com.ecm.promotion.dto.request.CreateDiscountRequest;
import com.ecm.promotion.dto.response.ApplyDiscountResponse;
import com.ecm.promotion.dto.response.DiscountResponse;
import com.ecm.promotion.entity.*;
import com.ecm.promotion.exception.PromotionErrorCode;
import com.ecm.promotion.repository.DiscountCategoryRepository;
import com.ecm.promotion.repository.DiscountRepository;
import com.ecm.promotion.repository.DiscountVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiscountService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int PERCENT_DENOMINATOR = 100;


    private final com.ecm.promotion.repository.DiscountUsageRepository usageRepository;
    private final DiscountRepository discountRepository;
    private final DiscountCategoryRepository categoryRepository;
    private final DiscountVariantRepository variantRepository;

    @Transactional
    public DiscountResponse create(CreateDiscountRequest request, UUID actorId) {
        // 1. Normalize and validate the discount contract
        String code = request.code() == null || request.code().isBlank() ? null : request.code().trim().toUpperCase(Locale.ROOT);
        validateDefinition(request, code);
        if (code != null && discountRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException(PromotionErrorCode.DISCOUNT_CODE_ALREADY_EXISTS, "Discount", "code", code);
        }

        // 2. Save the discount and scope targets atomically
        Discount discount = discountRepository.save(Discount.builder()
                .code(code).title(request.title().trim()).discountType(request.discountType()).value(request.value())
                .applicationScope(request.applicationScope()).minOrderAmount(request.minOrderAmount() == null ? 0L : request.minOrderAmount())
                .startAt(request.startAt()).endAt(request.endAt()).description(request.description())
                .usageLimit(request.usageLimit()).createdBy(actorId).status(DiscountStatus.ACTIVE).build());
        saveTargets(discount.getId(), request.applicationScope(), request.categoryIds(), request.variantIds());
        return toResponse(discount);
    }

    @Transactional
    public DiscountResponse update(UUID id, CreateDiscountRequest request, UUID actorId) {
        String code = request.code() == null || request.code().isBlank() ? null : request.code().trim().toUpperCase(Locale.ROOT);
        validateDefinition(request, code);
        Discount discount = discountRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Discount", id));
        if (discount.getStatus() == DiscountStatus.DELETED) {
            throw new BusinessException(PromotionErrorCode.DISCOUNT_INACTIVE);
        }
        if (code != null) {
            discountRepository.findByCodeIgnoreCase(code).filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> { throw new DuplicateResourceException(PromotionErrorCode.DISCOUNT_CODE_ALREADY_EXISTS,
                            "Discount", "code", code); });
        }
        discount.setCode(code);
        discount.setTitle(request.title().trim());
        discount.setDiscountType(request.discountType());
        discount.setValue(request.value());
        discount.setApplicationScope(request.applicationScope());
        discount.setMinOrderAmount(request.minOrderAmount() == null ? 0L : request.minOrderAmount());
        discount.setStartAt(request.startAt());
        discount.setEndAt(request.endAt());
        discount.setDescription(request.description());
        discount.setUsageLimit(request.usageLimit());
        discount.setUpdatedBy(actorId);
        categoryRepository.deleteByDiscountId(id);
        variantRepository.deleteByDiscountId(id);
        categoryRepository.flush();
        variantRepository.flush();
        saveTargets(id, request.applicationScope(), request.categoryIds(), request.variantIds());
        return toResponse(discountRepository.save(discount));
    }
    @Transactional(readOnly = true)
    public PageResponse<DiscountResponse> list(int page, int size) {
        // 1. Validate pagination bounds
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(com.ecm.common.exception.CommonErrorCode.BAD_REQUEST, "Invalid pagination bounds");
        }
        // 2. Load the page and map target relationships in batches
        Page<Discount> result = discountRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<UUID> ids = result.getContent().stream().map(Discount::getId).toList();
        var categories = categoryRepository.findByDiscountIdIn(ids).stream().collect(Collectors.groupingBy(
                DiscountCategory::getDiscountId, Collectors.mapping(DiscountCategory::getCategoryId, Collectors.toSet())));
        var variants = variantRepository.findByDiscountIdIn(ids).stream().collect(Collectors.groupingBy(
                DiscountVariant::getDiscountId, Collectors.mapping(DiscountVariant::getVariantId, Collectors.toSet())));
        Page<DiscountResponse> mapped = result.map(d -> toResponse(d, categories.getOrDefault(d.getId(), Set.of()),
                variants.getOrDefault(d.getId(), Set.of())));
        return PageResponse.of(mapped);
    }

    @Transactional(readOnly = true)
    public DiscountResponse get(UUID id) {
        return toResponse(findDiscount(id));
    }

    @Transactional
    public DiscountResponse setStatus(UUID id, DiscountStatus status, UUID actorId) {
        // 1. Find discount and apply administrative status change
        Discount discount = discountRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Discount", id));
        if (status == DiscountStatus.DELETED || discount.getStatus() == DiscountStatus.DELETED) {
            throw new BusinessException(com.ecm.common.exception.CommonErrorCode.BAD_REQUEST, "Use the delete endpoint to delete a discount");
        }
        discount.setStatus(status);
        discount.setUpdatedBy(actorId);
        return toResponse(discountRepository.save(discount));
    }

    @Transactional
    public void delete(UUID id) {
        // 1. Soft-delete to retain historical references from completed orders
        Discount discount = discountRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Discount", id));
        discount.setStatus(DiscountStatus.DELETED);
        discountRepository.save(discount);
    }

    @Transactional
    public ApplyDiscountResponse apply(ApplyDiscountRequest request) {
        // 1. Validate the submitted cart snapshot
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(PromotionErrorCode.DISCOUNT_NOT_APPLICABLE);
        }
        Set<UUID> seenVariants = new HashSet<>();
        long actualSubtotal = 0L;
        try {
            for (ApplyDiscountRequest.DiscountCartItemRequest item : request.items()) {
                if (item == null || item.productVariantId() == null || item.quantity() <= 0 || item.unitPrice() < 0
                        || !seenVariants.add(item.productVariantId())) {
                    throw new BusinessException(PromotionErrorCode.DISCOUNT_NOT_APPLICABLE, "Invalid or duplicate cart line");
                }
                actualSubtotal = Math.addExact(actualSubtotal, Math.multiplyExact(item.unitPrice(), item.quantity()));
            }
        } catch (ArithmeticException ex) {
            throw new BusinessException(PromotionErrorCode.INVALID_DISCOUNT_VALUE);
        }
        if (request.orderAmount() != actualSubtotal) {
            throw new BusinessException(PromotionErrorCode.DISCOUNT_NOT_APPLICABLE, "Submitted order amount does not match cart lines");
        }

        // 2. Calculate eligible item discounts; choose only the best promotion per line
        Map<UUID, Long> lineAmounts = request.items().stream().collect(Collectors.toMap(
                ApplyDiscountRequest.DiscountCartItemRequest::productVariantId,
                item -> Math.multiplyExact(item.unitPrice(), item.quantity())));
        List<ApplyDiscountResponse.ItemDiscountResponse> itemDiscounts = applyItemDiscounts(request.items(), lineAmounts);
        long itemDiscountTotal = itemDiscounts.stream().mapToLong(ApplyDiscountResponse.ItemDiscountResponse::discountAmount).reduce(0L, Math::addExact);

        // 3. Apply an optional order voucher to the remaining eligible subtotal
        UUID orderDiscountId = null;
        long orderDiscountAmount = 0;
        if (request.code() != null && !request.code().isBlank()) {
            Discount voucher = discountRepository.findByCodeIgnoreCase(request.code().trim())
                    .orElseThrow(() -> new BusinessException(PromotionErrorCode.DISCOUNT_NOT_APPLICABLE));
            validateActive(voucher, Instant.now(), actualSubtotal);
            if (voucher.getApplicationScope() != ApplicationScope.ORDER) {
                throw new BusinessException(PromotionErrorCode.DISCOUNT_NOT_APPLICABLE, "Voucher code must use ORDER scope");
            }
            long remainingSubtotal = actualSubtotal - itemDiscountTotal;
            orderDiscountAmount = Math.min(calculateDiscount(voucher, remainingSubtotal), remainingSubtotal);
            orderDiscountId = voucher.getId();
        }
        Set<UUID> appliedIds = itemDiscounts.stream().map(ApplyDiscountResponse.ItemDiscountResponse::discountId)
                .collect(Collectors.toCollection(TreeSet::new));
        if (orderDiscountId != null) appliedIds.add(orderDiscountId);
        for (UUID id : appliedIds) {
            Discount discount = discountRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Discount", id));
            validateActive(discount, Instant.now(), actualSubtotal);
            if (discount.getUsageLimit() != null) {
                if (request.checkoutKey() == null || request.checkoutKey().isBlank()) {
                    throw new BusinessException(PromotionErrorCode.DISCOUNT_NOT_APPLICABLE, "Checkout key required for limited discounts");
                }
                DiscountUsageId usageId = new DiscountUsageId(id, request.checkoutKey());
                if (!usageRepository.existsById(usageId)) {
                    if (usageRepository.countByDiscountId(id) >= discount.getUsageLimit()) {
                        throw new BusinessException(PromotionErrorCode.DISCOUNT_NOT_APPLICABLE, "Discount usage limit reached");
                    }
                    usageRepository.save(new DiscountUsage(id, request.checkoutKey()));
                }
            }
        }
        return new ApplyDiscountResponse(orderDiscountId, Math.addExact(itemDiscountTotal, orderDiscountAmount),
                orderDiscountId, orderDiscountAmount, itemDiscounts);
    }

    private List<ApplyDiscountResponse.ItemDiscountResponse> applyItemDiscounts(
            List<ApplyDiscountRequest.DiscountCartItemRequest> items, Map<UUID, Long> lineAmounts) {
        if (items.isEmpty()) return List.of();
        Instant now = Instant.now();
        Set<UUID> variantIds = items.stream().map(ApplyDiscountRequest.DiscountCartItemRequest::productVariantId).collect(Collectors.toSet());
        List<DiscountVariant> variantTargets = variantRepository.findByVariantIdIn(variantIds);
        Map<UUID, Set<UUID>> variantDiscounts = variantTargets.stream().collect(Collectors.groupingBy(
                DiscountVariant::getVariantId, Collectors.mapping(DiscountVariant::getDiscountId, Collectors.toSet())));
        Set<UUID> categoryIds = items.stream().map(ApplyDiscountRequest.DiscountCartItemRequest::categoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Set<UUID>> categoryDiscounts = categoryIds.isEmpty() ? Map.of() : categoryRepository.findByCategoryIdIn(categoryIds).stream()
                .collect(Collectors.groupingBy(DiscountCategory::getCategoryId, Collectors.mapping(DiscountCategory::getDiscountId, Collectors.toSet())));
        Set<UUID> candidateIds = new HashSet<>();
        variantDiscounts.values().forEach(candidateIds::addAll);
        categoryDiscounts.values().forEach(candidateIds::addAll);
        List<Discount> activeGlobalDiscounts = discountRepository.findActiveAllItems(DiscountStatus.ACTIVE, now);
        candidateIds.addAll(activeGlobalDiscounts.stream().map(Discount::getId).toList());
        Map<UUID, Discount> discounts = discountRepository.findByIdIn(candidateIds).stream()
                .collect(Collectors.toMap(Discount::getId, discount -> discount));

        List<ApplyDiscountResponse.ItemDiscountResponse> results = new ArrayList<>();
        for (ApplyDiscountRequest.DiscountCartItemRequest item : items) {
            long lineAmount = lineAmounts.get(item.productVariantId());
            Discount bestDiscount = candidateIds.stream().map(discounts::get).filter(Objects::nonNull)
                    .filter(discount -> discount.getApplicationScope() == ApplicationScope.ALL_ITEMS
                            || variantDiscounts.getOrDefault(item.productVariantId(), Set.of()).contains(discount.getId())
                            || item.categoryId() != null && categoryDiscounts.getOrDefault(item.categoryId(), Set.of()).contains(discount.getId()))
                    .filter(discount -> isActive(discount, now, lineAmount))
                    .max(Comparator.comparingLong(discount -> calculateDiscount(discount, lineAmount)))
                    .orElse(null);
            if (bestDiscount != null) {
                results.add(new ApplyDiscountResponse.ItemDiscountResponse(item.productVariantId(), bestDiscount.getId(),
                        Math.min(calculateDiscount(bestDiscount, lineAmount), lineAmount)));
            }
        }
        return results;
    }

    private long calculateDiscount(Discount discount, long amount) {
        if (discount.getDiscountType() == DiscountType.FIXED) return Math.min(discount.getValue(), amount);
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(discount.getValue()))
                .divide(BigDecimal.valueOf(PERCENT_DENOMINATOR), 0, RoundingMode.DOWN).longValueExact();
    }

    private boolean isActive(Discount discount, Instant now, long amount) {
        return discount.getStatus() == DiscountStatus.ACTIVE
                && !now.isBefore(discount.getStartAt()) && now.isBefore(discount.getEndAt())
                && amount >= discount.getMinOrderAmount();
    }

    private void validateActive(Discount discount, Instant now, long orderAmount) {
        if (!isActive(discount, now, Long.MAX_VALUE)) {
            throw new BusinessException(PromotionErrorCode.DISCOUNT_INACTIVE);
        }
        if (orderAmount < discount.getMinOrderAmount()) {
            throw new BusinessException(PromotionErrorCode.MINIMUM_ORDER_NOT_MET);
        }
    }

    private void validateDefinition(CreateDiscountRequest request, String code) {
        if (request.startAt() == null || request.endAt() == null || !request.startAt().isBefore(request.endAt())
                || request.minOrderAmount() != null && request.minOrderAmount() < 0
                || request.value() <= 0 || request.discountType() == DiscountType.PERCENT && request.value() > 100
                || request.usageLimit() != null && request.usageLimit() <= 0
                || request.applicationScope() == ApplicationScope.ORDER && code == null
                || request.applicationScope() != ApplicationScope.ORDER && code != null) {
            throw new BusinessException(PromotionErrorCode.INVALID_DISCOUNT_VALUE);
        }
        validateTargets(request.applicationScope(), request.categoryIds(), request.variantIds());
    }

    private void validateTargets(ApplicationScope scope, Set<UUID> categories, Set<UUID> variants) {
        boolean hasCategories = categories != null && !categories.isEmpty();
        boolean hasVariants = variants != null && !variants.isEmpty();
        boolean valid = switch (scope) {
            case ORDER, ALL_ITEMS -> !hasCategories && !hasVariants;
            case CATEGORY -> hasCategories && !hasVariants;
            case VARIANT -> !hasCategories && hasVariants;
        };
        if (!valid) throw new BusinessException(PromotionErrorCode.INVALID_DISCOUNT_TARGETS);
    }

    private void saveTargets(UUID discountId, ApplicationScope scope, Set<UUID> categories, Set<UUID> variants) {
        if (scope == ApplicationScope.CATEGORY) {
            categoryRepository.saveAll(categories.stream().map(id -> DiscountCategory.builder().discountId(discountId).categoryId(id).build()).toList());
        }
        if (scope == ApplicationScope.VARIANT) {
            variantRepository.saveAll(variants.stream().map(id -> DiscountVariant.builder().discountId(discountId).variantId(id).build()).toList());
        }
    }

    private Discount findDiscount(UUID id) {
        return discountRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Discount", id));
    }

    private DiscountResponse toResponse(Discount discount) {
        Set<UUID> categories = categoryRepository.findByDiscountId(discount.getId()).stream().map(DiscountCategory::getCategoryId).collect(Collectors.toSet());
        Set<UUID> variants = variantRepository.findByDiscountId(discount.getId()).stream().map(DiscountVariant::getVariantId).collect(Collectors.toSet());
        return toResponse(discount, categories, variants);
    }

    private DiscountResponse toResponse(Discount discount, Set<UUID> categories, Set<UUID> variants) {
        return new DiscountResponse(discount.getId(), discount.getCode(), discount.getTitle(), discount.getDiscountType(),
                discount.getValue(), discount.getApplicationScope(), discount.getMinOrderAmount(), discount.getStartAt(),
                discount.getEndAt(), discount.getDescription(), discount.getStatus(), categories, variants, discount.getUsageLimit());
    }
}
