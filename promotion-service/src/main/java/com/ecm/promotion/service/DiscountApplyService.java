package com.ecm.promotion.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.promotion.dto.request.ApplyDiscountRequest;
import com.ecm.promotion.dto.request.ApplyDiscountRequest.DiscountCartItemRequest;
import com.ecm.promotion.dto.response.ApplyDiscountResponse;
import com.ecm.promotion.dto.response.ApplyDiscountResponse.ItemDiscountResponse;
import com.ecm.promotion.entity.ApplicationScope;
import com.ecm.promotion.entity.Discount;
import com.ecm.promotion.entity.DiscountCategory;
import com.ecm.promotion.entity.DiscountStatus;
import com.ecm.promotion.entity.DiscountType;
import com.ecm.promotion.exception.PromotionErrorCode;
import com.ecm.promotion.repository.DiscountCategoryRepository;
import com.ecm.promotion.repository.DiscountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Prices a cart for checkout. Each line gets the single best ALL_ITEMS or CATEGORY discount that is in effect, and an
 * order-level discount is taken off what remains: the ORDER voucher when a code is given, otherwise the best ORDER
 * discount that has no code. Expiry is checked here, at query time.
 */
@Service
@RequiredArgsConstructor
public class DiscountApplyService {

    private static final int PERCENT_DENOMINATOR = 100;

    private final DiscountRepository discountRepository;
    private final DiscountCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public ApplyDiscountResponse apply(ApplyDiscountRequest request) {
        // 1. Validate the submitted cart snapshot
        long subtotal = subtotal(request.items());
        if (request.orderAmount() != subtotal) {
            throw new BusinessException(PromotionErrorCode.DISCOUNT_NOT_APPLICABLE, "Submitted order amount does not match cart lines");
        }

        // 2. Best item discount per line
        Instant now = Instant.now();
        List<ItemDiscountResponse> itemDiscounts = applyItemDiscounts(request.items(), now);
        long itemDiscountTotal = itemDiscounts.stream().mapToLong(ItemDiscountResponse::discountAmount).reduce(0L, Math::addExact);

        // 3. One order-level discount on the remaining subtotal: the voucher when a code is given, else the best automatic one
        long remainingSubtotal = subtotal - itemDiscountTotal;
        Discount orderDiscount = hasCode(request) ? voucher(request.code(), subtotal, now) : bestAutomaticOrderDiscount(subtotal, remainingSubtotal, now);
        UUID orderDiscountId = orderDiscount == null ? null : orderDiscount.getId();
        long orderDiscountAmount = orderDiscount == null ? 0 : Math.min(calculateDiscount(orderDiscount, remainingSubtotal), remainingSubtotal);
        return new ApplyDiscountResponse(orderDiscountId, Math.addExact(itemDiscountTotal, orderDiscountAmount),
                orderDiscountId, orderDiscountAmount, itemDiscounts);
    }

    private static boolean hasCode(ApplyDiscountRequest request) {
        return request.code() != null && !request.code().isBlank();
    }

    private Discount voucher(String code, long subtotal, Instant now) {
        Discount voucher = discountRepository.findByCodeIgnoreCaseAndStatusNot(code.trim(), DiscountStatus.DELETED)
                .orElseThrow(() -> new BusinessException(PromotionErrorCode.DISCOUNT_NOT_APPLICABLE));
        if (voucher.getApplicationScope() != ApplicationScope.ORDER) {
            throw new BusinessException(PromotionErrorCode.DISCOUNT_NOT_APPLICABLE, "Voucher code must use ORDER scope");
        }
        if (!inEffect(voucher, now)) {
            throw new BusinessException(PromotionErrorCode.DISCOUNT_INACTIVE);
        }
        if (subtotal < voucher.getMinOrderAmount()) {
            throw new BusinessException(PromotionErrorCode.MINIMUM_ORDER_NOT_MET);
        }
        return voucher;
    }

    private Discount bestAutomaticOrderDiscount(long subtotal, long remainingSubtotal, Instant now) {
        return discountRepository.findActiveAutomaticOrder(DiscountStatus.ACTIVE, now).stream()
                .filter(discount -> subtotal >= discount.getMinOrderAmount())
                .max(Comparator.comparingLong(discount -> calculateDiscount(discount, remainingSubtotal)))
                .orElse(null);
    }

    private long subtotal(List<DiscountCartItemRequest> items) {
        Set<UUID> seenVariants = new HashSet<>();
        long subtotal = 0L;
        try {
            for (DiscountCartItemRequest item : items) {
                if (!seenVariants.add(item.productVariantId())) {
                    throw new BusinessException(PromotionErrorCode.DISCOUNT_NOT_APPLICABLE, "Duplicate cart line");
                }
                subtotal = Math.addExact(subtotal, Math.multiplyExact(item.unitPrice(), (long) item.quantity()));
            }
        } catch (ArithmeticException ex) {
            throw new BusinessException(PromotionErrorCode.INVALID_DISCOUNT_VALUE);
        }
        return subtotal;
    }

    private List<ItemDiscountResponse> applyItemDiscounts(List<DiscountCartItemRequest> items, Instant now) {
        // 1. Candidates: every ALL_ITEMS discount in effect, plus those targeting the categories in the cart
        Set<UUID> categoryIds = items.stream().map(DiscountCartItemRequest::categoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Set<UUID>> discountsByCategory = categoryIds.isEmpty() ? Map.of()
                : categoryRepository.findByCategoryIdIn(categoryIds).stream().collect(Collectors.groupingBy(
                        DiscountCategory::getCategoryId, Collectors.mapping(DiscountCategory::getDiscountId, Collectors.toSet())));
        Set<UUID> categoryDiscountIds = new HashSet<>();
        discountsByCategory.values().forEach(categoryDiscountIds::addAll);
        List<Discount> candidates = new ArrayList<>(discountRepository.findByIdIn(categoryDiscountIds));
        candidates.addAll(discountRepository.findActiveAllItems(DiscountStatus.ACTIVE, now));
        Map<UUID, Discount> byId = candidates.stream().collect(Collectors.toMap(Discount::getId, Function.identity(), (a, b) -> a));

        // 2. The best one per line
        return items.stream().map(item -> {
            long lineAmount = Math.multiplyExact(item.unitPrice(), (long) item.quantity());
            Set<UUID> categoryDiscounts = item.categoryId() == null ? Set.of() : discountsByCategory.getOrDefault(item.categoryId(), Set.of());
            return byId.values().stream()
                    .filter(discount -> discount.getApplicationScope() == ApplicationScope.ALL_ITEMS
                            || discount.getApplicationScope() == ApplicationScope.CATEGORY && categoryDiscounts.contains(discount.getId()))
                    .filter(discount -> inEffect(discount, now) && lineAmount >= discount.getMinOrderAmount())
                    .max(Comparator.comparingLong(discount -> calculateDiscount(discount, lineAmount)))
                    .map(best -> new ItemDiscountResponse(item.productVariantId(), best.getId(),
                            Math.min(calculateDiscount(best, lineAmount), lineAmount)))
                    .orElse(null);
        }).filter(Objects::nonNull).toList();
    }

    private long calculateDiscount(Discount discount, long amount) {
        if (discount.getDiscountType() == DiscountType.FIXED) {
            return Math.min(discount.getValue(), amount);
        }
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(discount.getValue()))
                .divide(BigDecimal.valueOf(PERCENT_DENOMINATOR), 0, RoundingMode.DOWN).longValueExact();
    }

    private boolean inEffect(Discount discount, Instant now) {
        return discount.getStatus() == DiscountStatus.ACTIVE
                && !now.isBefore(discount.getStartAt()) && now.isBefore(discount.getEndAt());
    }
}
