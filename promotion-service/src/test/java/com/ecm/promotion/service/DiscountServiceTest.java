package com.ecm.promotion.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.promotion.dto.request.ApplyDiscountRequest;
import com.ecm.promotion.entity.*;
import com.ecm.promotion.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {
    @Mock DiscountRepository discountRepository;
    @Mock DiscountCategoryRepository categoryRepository;
    @Mock DiscountVariantRepository variantRepository;
    @Mock DiscountUsageRepository usageRepository;
    @InjectMocks DiscountService service;
    private final UUID variant = UUID.randomUUID();

    private Discount discount(ApplicationScope scope, DiscountType type, int value) {
        return Discount.builder().id(UUID.randomUUID()).applicationScope(scope).discountType(type).value(value)
                .status(DiscountStatus.ACTIVE).minOrderAmount(0L).startAt(Instant.now().minusSeconds(60))
                .endAt(Instant.now().plusSeconds(3600)).build();
    }

    private ApplyDiscountRequest request(String code, long price, int quantity) {
        return new ApplyDiscountRequest(code, price * quantity,
                List.of(new ApplyDiscountRequest.DiscountCartItemRequest(variant, quantity, price, null)), "checkout");
    }

    private void candidates(Discount... discounts) {
        when(discountRepository.findActiveAllItems(eq(DiscountStatus.ACTIVE), any())).thenReturn(List.of(discounts));
        when(discountRepository.findByIdIn(any())).thenReturn(List.of(discounts));
        for (Discount d : discounts) lenient().when(discountRepository.lockById(d.getId())).thenReturn(Optional.of(d));
    }

    @Test void stacksBestItemPromotionThenVoucher() {
        Discount fixed = discount(ApplicationScope.ALL_ITEMS, DiscountType.FIXED, 30);
        Discount percent = discount(ApplicationScope.ALL_ITEMS, DiscountType.PERCENT, 20);
        Discount voucher = discount(ApplicationScope.ORDER, DiscountType.PERCENT, 10);
        candidates(fixed, percent);
        when(discountRepository.findByCodeIgnoreCase("SAVE")).thenReturn(Optional.of(voucher));
        when(discountRepository.lockById(voucher.getId())).thenReturn(Optional.of(voucher));
        var result = service.apply(request("SAVE", 100, 2));
        assertEquals(56, result.discountAmount());
        assertEquals(16, result.orderDiscountAmount());
        assertEquals(percent.getId(), result.itemDiscounts().getFirst().discountId());
        assertEquals(40, result.itemDiscounts().getFirst().discountAmount());
    }

    @Test void capsFixedDiscountAtLineAmount() {
        candidates(discount(ApplicationScope.ALL_ITEMS, DiscountType.FIXED, 999));
        assertEquals(100, service.apply(request(null, 100, 1)).discountAmount());
    }

    @Test void rejectsInactiveVoucher() {
        candidates();
        Discount d = discount(ApplicationScope.ORDER, DiscountType.FIXED, 10);
        d.setEndAt(Instant.now().minusSeconds(1));
        when(discountRepository.findByCodeIgnoreCase("OLD")).thenReturn(Optional.of(d));
        assertThrows(BusinessException.class, () -> service.apply(request("OLD", 100, 1)));
    }

    @Test void rejectsMinimumNotMet() {
        candidates();
        Discount d = discount(ApplicationScope.ORDER, DiscountType.FIXED, 10);
        d.setMinOrderAmount(200L);
        when(discountRepository.findByCodeIgnoreCase("MIN")).thenReturn(Optional.of(d));
        assertThrows(BusinessException.class, () -> service.apply(request("MIN", 100, 1)));
    }

    @Test void rejectsDuplicateVariantLines() {
        var line = new ApplyDiscountRequest.DiscountCartItemRequest(variant, 1, 100, null);
        assertThrows(BusinessException.class, () -> service.apply(new ApplyDiscountRequest(null, 200, List.of(line, line), "key")));
        verifyNoInteractions(discountRepository);
    }

    @Test void rejectsSubtotalMismatchAndOverflow() {
        assertThrows(BusinessException.class, () -> service.apply(new ApplyDiscountRequest(null, 1,
                request(null, 100, 1).items(), "key")));
        assertThrows(BusinessException.class, () -> service.apply(new ApplyDiscountRequest(null, 1,
                List.of(new ApplyDiscountRequest.DiscountCartItemRequest(variant, 2, Long.MAX_VALUE, null)), "key")));
    }

    @Test void rejectsExhaustedQuotaUnderLock() {
        Discount d = discount(ApplicationScope.ALL_ITEMS, DiscountType.FIXED, 10);
        d.setUsageLimit(1L);
        candidates(d);
        when(usageRepository.countByDiscountId(d.getId())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.apply(request(null, 100, 1)));
        verify(usageRepository, never()).save(any());
    }

    @Test void retryDoesNotConsumeQuotaAgain() {
        Discount d = discount(ApplicationScope.ALL_ITEMS, DiscountType.FIXED, 10);
        d.setUsageLimit(1L);
        candidates(d);
        when(usageRepository.existsById(any())).thenReturn(true);
        assertEquals(10, service.apply(request(null, 100, 1)).discountAmount());
        verify(usageRepository, never()).save(any());
    }

    @Test void recordsLimitedDiscountOncePerCheckout() {
        Discount d = discount(ApplicationScope.ALL_ITEMS, DiscountType.FIXED, 10);
        d.setUsageLimit(2L);
        candidates(d);
        service.apply(request(null, 100, 1));
        verify(usageRepository).save(argThat(u -> u.getDiscountId().equals(d.getId()) && u.getCheckoutKey().equals("checkout")));
    }
}
