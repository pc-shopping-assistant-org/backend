package com.ecm.promotion.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.promotion.dto.request.ApplyDiscountRequest;
import com.ecm.promotion.dto.request.ApplyDiscountRequest.DiscountCartItemRequest;
import com.ecm.promotion.dto.response.ApplyDiscountResponse;
import com.ecm.promotion.entity.ApplicationScope;
import com.ecm.promotion.entity.Discount;
import com.ecm.promotion.entity.DiscountCategory;
import com.ecm.promotion.entity.DiscountStatus;
import com.ecm.promotion.entity.DiscountType;
import com.ecm.promotion.exception.PromotionErrorCode;
import com.ecm.promotion.repository.DiscountCategoryRepository;
import com.ecm.promotion.repository.DiscountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DiscountApplyServiceTest {

    private static final UUID VARIANT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CATEGORY = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private DiscountRepository discountRepository;
    private DiscountCategoryRepository categoryRepository;
    private DiscountApplyService service;

    @BeforeEach
    void setUp() {
        discountRepository = mock(DiscountRepository.class);
        categoryRepository = mock(DiscountCategoryRepository.class);
        service = new DiscountApplyService(discountRepository, categoryRepository);
        when(discountRepository.findActiveAllItems(eq(DiscountStatus.ACTIVE), any())).thenReturn(List.of());
        when(discountRepository.findByIdIn(anyCollection())).thenReturn(List.of());
    }

    private Discount discount(ApplicationScope scope, DiscountType type, int value) {
        return Discount.builder().id(UUID.randomUUID()).applicationScope(scope).discountType(type).value(value)
                .status(DiscountStatus.ACTIVE).minOrderAmount(0L).startAt(Instant.now().minusSeconds(60))
                .endAt(Instant.now().plusSeconds(3600)).build();
    }

    private ApplyDiscountRequest request(String code, long price, int quantity, UUID categoryId) {
        return new ApplyDiscountRequest(code, price * quantity,
                List.of(new DiscountCartItemRequest(VARIANT, quantity, price, categoryId)));
    }

    private void allItems(Discount... discounts) {
        when(discountRepository.findActiveAllItems(eq(DiscountStatus.ACTIVE), any())).thenReturn(List.of(discounts));
    }

    @Test
    void stacksTheBestItemDiscountThenTheVoucher() {
        Discount fixed = discount(ApplicationScope.ALL_ITEMS, DiscountType.FIXED, 30);
        Discount percent = discount(ApplicationScope.ALL_ITEMS, DiscountType.PERCENT, 20);
        Discount voucher = discount(ApplicationScope.ORDER, DiscountType.PERCENT, 10);
        allItems(fixed, percent);
        when(discountRepository.findByCodeIgnoreCaseAndStatusNot("SAVE", DiscountStatus.DELETED)).thenReturn(Optional.of(voucher));

        ApplyDiscountResponse result = service.apply(request("SAVE", 100, 2, null));

        assertEquals(56, result.discountAmount());
        assertEquals(16, result.orderDiscountAmount());
        assertEquals(percent.getId(), result.itemDiscounts().getFirst().discountId());
        assertEquals(40, result.itemDiscounts().getFirst().discountAmount());
    }

    @Test
    void aCategoryDiscountOnlyAppliesToLinesInThatCategory() {
        Discount categoryDiscount = discount(ApplicationScope.CATEGORY, DiscountType.PERCENT, 50);
        when(categoryRepository.findByCategoryIdIn(anyCollection()))
                .thenReturn(List.of(DiscountCategory.builder().discountId(categoryDiscount.getId()).categoryId(CATEGORY).build()));
        when(discountRepository.findByIdIn(anyCollection())).thenReturn(List.of(categoryDiscount));

        assertEquals(50, service.apply(request(null, 100, 1, CATEGORY)).discountAmount());
        assertEquals(0, service.apply(request(null, 100, 1, UUID.randomUUID())).discountAmount());
        assertEquals(0, service.apply(request(null, 100, 1, null)).discountAmount());
    }

    @Test
    void capsAFixedDiscountAtTheLineAmount() {
        allItems(discount(ApplicationScope.ALL_ITEMS, DiscountType.FIXED, 999));

        assertEquals(100, service.apply(request(null, 100, 1, null)).discountAmount());
    }

    @Test
    void anItemDiscountBelowItsMinimumDoesNotApply() {
        Discount d = discount(ApplicationScope.ALL_ITEMS, DiscountType.FIXED, 10);
        d.setMinOrderAmount(200L);
        allItems(d);

        assertTrue(service.apply(request(null, 100, 1, null)).itemDiscounts().isEmpty());
    }

    @Test
    void anExpiredOrLockedDiscountDoesNotApply() {
        Discount expired = discount(ApplicationScope.ALL_ITEMS, DiscountType.FIXED, 10);
        expired.setEndAt(Instant.now().minusSeconds(1));
        Discount locked = discount(ApplicationScope.ALL_ITEMS, DiscountType.FIXED, 10);
        locked.setStatus(DiscountStatus.INACTIVE);
        allItems(expired, locked);

        assertTrue(service.apply(request(null, 100, 1, null)).itemDiscounts().isEmpty());
    }

    @Test
    void rejectsAnExpiredVoucher() {
        Discount d = discount(ApplicationScope.ORDER, DiscountType.FIXED, 10);
        d.setEndAt(Instant.now().minusSeconds(1));
        when(discountRepository.findByCodeIgnoreCaseAndStatusNot("OLD", DiscountStatus.DELETED)).thenReturn(Optional.of(d));

        assertEquals(PromotionErrorCode.DISCOUNT_INACTIVE,
                ((PromotionErrorCode) assertThrows(BusinessException.class, () -> service.apply(request("OLD", 100, 1, null))).getErrorCode()));
    }

    @Test
    void rejectsAVoucherWhenTheMinimumOrderIsNotMet() {
        Discount d = discount(ApplicationScope.ORDER, DiscountType.FIXED, 10);
        d.setMinOrderAmount(200L);
        when(discountRepository.findByCodeIgnoreCaseAndStatusNot("MIN", DiscountStatus.DELETED)).thenReturn(Optional.of(d));

        assertEquals(PromotionErrorCode.MINIMUM_ORDER_NOT_MET,
                ((PromotionErrorCode) assertThrows(BusinessException.class, () -> service.apply(request("MIN", 100, 1, null))).getErrorCode()));
    }

    @Test
    void aCodeOfAnotherScopeOrAnUnknownCodeIsNotApplicable() {
        Discount notOrder = discount(ApplicationScope.ALL_ITEMS, DiscountType.FIXED, 10);
        when(discountRepository.findByCodeIgnoreCaseAndStatusNot("ALL", DiscountStatus.DELETED)).thenReturn(Optional.of(notOrder));
        when(discountRepository.findByCodeIgnoreCaseAndStatusNot("NONE", DiscountStatus.DELETED)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.apply(request("ALL", 100, 1, null)));
        assertThrows(BusinessException.class, () -> service.apply(request("NONE", 100, 1, null)));
    }

    @Test
    void rejectsDuplicateLinesBeforeLookingUpAnyDiscount() {
        DiscountCartItemRequest line = new DiscountCartItemRequest(VARIANT, 1, 100, null);

        assertThrows(BusinessException.class, () -> service.apply(new ApplyDiscountRequest(null, 200, List.of(line, line))));
        verifyNoInteractions(discountRepository);
    }

    @Test
    void rejectsASubtotalMismatchAndOverflow() {
        assertThrows(BusinessException.class, () -> service.apply(new ApplyDiscountRequest(null, 1, request(null, 100, 1, null).items())));
        assertThrows(BusinessException.class, () -> service.apply(new ApplyDiscountRequest(null, 1,
                List.of(new DiscountCartItemRequest(VARIANT, 2, Long.MAX_VALUE, null)))));
    }

    private void automaticOrder(Discount... discounts) {
        when(discountRepository.findActiveAutomaticOrder(eq(DiscountStatus.ACTIVE), any())).thenReturn(List.of(discounts));
    }

    @Test
    void withoutACodeTheBestAutomaticOrderDiscountApplies() {
        Discount small = discount(ApplicationScope.ORDER, DiscountType.PERCENT, 5);
        Discount big = discount(ApplicationScope.ORDER, DiscountType.PERCENT, 10);
        automaticOrder(small, big);

        ApplyDiscountResponse result = service.apply(request(null, 100, 2, null));

        assertEquals(big.getId(), result.orderDiscountId());
        assertEquals(20, result.orderDiscountAmount());
        assertEquals(20, result.discountAmount());
    }

    @Test
    void anAutomaticOrderDiscountBelowItsMinimumDoesNotApply() {
        Discount d = discount(ApplicationScope.ORDER, DiscountType.FIXED, 10);
        d.setMinOrderAmount(500L);
        automaticOrder(d);

        ApplyDiscountResponse result = service.apply(request(null, 100, 2, null));

        assertNull(result.orderDiscountId());
        assertEquals(0, result.discountAmount());
    }

    @Test
    void aVoucherCodeReplacesTheAutomaticOrderDiscount() {
        Discount automatic = discount(ApplicationScope.ORDER, DiscountType.PERCENT, 50);
        Discount voucher = discount(ApplicationScope.ORDER, DiscountType.PERCENT, 10);
        automaticOrder(automatic);
        when(discountRepository.findByCodeIgnoreCaseAndStatusNot("SAVE", DiscountStatus.DELETED)).thenReturn(Optional.of(voucher));

        ApplyDiscountResponse result = service.apply(request("SAVE", 100, 1, null));

        assertEquals(voucher.getId(), result.orderDiscountId());
        assertEquals(10, result.orderDiscountAmount());
    }

    @Test
    void theAutomaticOrderDiscountIsTakenOffWhatTheItemDiscountsLeave() {
        allItems(discount(ApplicationScope.ALL_ITEMS, DiscountType.PERCENT, 50));
        automaticOrder(discount(ApplicationScope.ORDER, DiscountType.PERCENT, 10));

        ApplyDiscountResponse result = service.apply(request(null, 100, 2, null));

        assertEquals(10, result.orderDiscountAmount());
        assertEquals(110, result.discountAmount());
    }
}
