package com.ecm.promotion.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.DuplicateResourceException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.promotion.dto.request.CreateDiscountRequest;
import com.ecm.promotion.dto.request.UpdateDiscountRequest;
import com.ecm.promotion.dto.response.DiscountResponse;
import com.ecm.promotion.entity.ApplicationScope;
import com.ecm.promotion.entity.Discount;
import com.ecm.promotion.entity.DiscountCategory;
import com.ecm.promotion.entity.DiscountState;
import com.ecm.promotion.entity.DiscountStatus;
import com.ecm.promotion.entity.DiscountType;
import com.ecm.promotion.exception.PromotionErrorCode;
import com.ecm.promotion.mapper.DiscountMapper;
import com.ecm.promotion.repository.DiscountCategoryRepository;
import com.ecm.promotion.repository.DiscountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscountServiceTest {

    private static final UUID EMPLOYEE = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID DISCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID CATEGORY_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CATEGORY_B = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    private final Instant now = Instant.now();
    private DiscountRepository discountRepository;
    private DiscountCategoryRepository categoryRepository;
    private DiscountCategoryChecker categoryChecker;
    private DiscountService service;

    @BeforeEach
    void setUp() {
        discountRepository = mock(DiscountRepository.class);
        categoryRepository = mock(DiscountCategoryRepository.class);
        categoryChecker = mock(DiscountCategoryChecker.class);
        DiscountMapper mapper = (discount, state, categoryIds) -> new DiscountResponse(discount.getId(), discount.getCode(),
                discount.getTitle(), discount.getDiscountType(), discount.getValue(), discount.getApplicationScope(),
                discount.getMinOrderAmount(), discount.getStartAt(), discount.getEndAt(), discount.getDescription(),
                discount.getStatus(), state, categoryIds, discount.getCreatedAt(), discount.getUpdatedAt());
        service = new DiscountService(discountRepository, categoryRepository, categoryChecker, mapper);
        when(discountRepository.save(any(Discount.class))).thenAnswer(call -> {
            Discount saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(DISCOUNT_ID);
            }
            return saved;
        });
    }

    private CreateDiscountRequest create(ApplicationScope scope, String code, Set<UUID> categories) {
        return new CreateDiscountRequest(code, "  Summer sale ", DiscountType.PERCENT, 10, scope, null,
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), null, categories);
    }

    private UpdateDiscountRequest update(ApplicationScope scope, String code, Set<UUID> categories) {
        return new UpdateDiscountRequest(code, "Edited", DiscountType.FIXED, 5000, scope, 100L,
                now.minus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS), "d", categories);
    }

    private Discount discount(DiscountStatus status, Instant startAt, Instant endAt) {
        return Discount.builder().id(DISCOUNT_ID).code("OLD").title("Old").discountType(DiscountType.PERCENT).value(10)
                .applicationScope(ApplicationScope.ORDER).minOrderAmount(0L).startAt(startAt).endAt(endAt).status(status).build();
    }

    private Discount running() {
        return discount(DiscountStatus.ACTIVE, now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS));
    }

    private static PromotionErrorCode code(BusinessException ex) {
        return (PromotionErrorCode) ex.getErrorCode();
    }

    // ---- UC-ADM-DIS-002 add ----

    @Test
    void createsAnOrderVoucherWithTheCodeUpperCased() {
        DiscountResponse response = service.create(create(ApplicationScope.ORDER, " save10 ", null), EMPLOYEE);

        ArgumentCaptor<Discount> saved = ArgumentCaptor.forClass(Discount.class);
        verify(discountRepository).save(saved.capture());
        assertEquals("SAVE10", saved.getValue().getCode());
        assertEquals("Summer sale", saved.getValue().getTitle());
        assertEquals(0L, saved.getValue().getMinOrderAmount());
        assertEquals(EMPLOYEE, saved.getValue().getCreatedBy());
        assertEquals(DiscountStatus.ACTIVE, saved.getValue().getStatus());
        assertEquals(DiscountState.RUNNING, response.state());
        verify(categoryChecker, never()).requireInUse(any());
    }

    @Test
    void anOrderDiscountMayHaveNoCode() {
        DiscountResponse response = service.create(create(ApplicationScope.ORDER, " ", null), EMPLOYEE);

        assertNull(response.code());
    }

    @Test
    void createsACategoryDiscountWithItsTargetsAfterCheckingThemWithCatalog() {
        Set<UUID> targets = Set.of(CATEGORY_A, CATEGORY_B);

        DiscountResponse response = service.create(create(ApplicationScope.CATEGORY, null, targets), EMPLOYEE);

        verify(categoryChecker).requireInUse(targets);
        ArgumentCaptor<List<DiscountCategory>> rows = ArgumentCaptor.forClass(List.class);
        verify(categoryRepository).saveAll(rows.capture());
        assertEquals(2, rows.getValue().size());
        assertEquals(targets, response.categoryIds());
    }

    @Test
    void rejectsAPercentOutsideZeroToOneHundred() {
        for (int percent : new int[]{0, 101}) {
            CreateDiscountRequest request = new CreateDiscountRequest(null, "t", DiscountType.PERCENT, percent,
                    ApplicationScope.ALL_ITEMS, null, now, now.plusSeconds(60), null, null);
            assertEquals(PromotionErrorCode.INVALID_DISCOUNT_VALUE,
                    code(assertThrows(BusinessException.class, () -> service.create(request, EMPLOYEE))));
        }
    }

    @Test
    void aFixedAmountOverOneHundredIsAllowed() {
        CreateDiscountRequest request = new CreateDiscountRequest(null, "t", DiscountType.FIXED, 500_000,
                ApplicationScope.ALL_ITEMS, null, now, now.plusSeconds(60), null, null);

        assertEquals(500_000, service.create(request, EMPLOYEE).value());
    }

    @Test
    void rejectsAStartThatIsNotBeforeTheEnd() {
        CreateDiscountRequest request = new CreateDiscountRequest(null, "t", DiscountType.FIXED, 5,
                ApplicationScope.ALL_ITEMS, null, now, now, null, null);

        assertEquals(PromotionErrorCode.INVALID_DISCOUNT_VALUE,
                code(assertThrows(BusinessException.class, () -> service.create(request, EMPLOYEE))));
    }

    @Test
    void rejectsTargetsOnOrderAndAllItemsAndMissingTargetsOnCategory() {
        for (ApplicationScope scope : new ApplicationScope[]{ApplicationScope.ORDER, ApplicationScope.ALL_ITEMS}) {
            assertEquals(PromotionErrorCode.INVALID_DISCOUNT_TARGETS, code(assertThrows(BusinessException.class,
                    () -> service.create(create(scope, null, Set.of(CATEGORY_A)), EMPLOYEE))));
        }
        for (Set<UUID> none : new Set[]{null, Set.<UUID>of()}) {
            assertEquals(PromotionErrorCode.INVALID_DISCOUNT_TARGETS, code(assertThrows(BusinessException.class,
                    () -> service.create(create(ApplicationScope.CATEGORY, null, none), EMPLOYEE))));
        }
        verify(discountRepository, never()).save(any());
    }

    @Test
    void rejectsACodeOnAnyScopeButOrder() {
        for (ApplicationScope scope : new ApplicationScope[]{ApplicationScope.ALL_ITEMS, ApplicationScope.CATEGORY}) {
            Set<UUID> targets = scope == ApplicationScope.CATEGORY ? Set.of(CATEGORY_A) : null;
            assertEquals(PromotionErrorCode.INVALID_DISCOUNT_CODE, code(assertThrows(BusinessException.class,
                    () -> service.create(create(scope, "CODE", targets), EMPLOYEE))));
        }
    }

    @Test
    void rejectsADuplicateCode() {
        when(discountRepository.existsByCodeIgnoreCaseAndStatusNot("SAVE10", DiscountStatus.DELETED)).thenReturn(true);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> service.create(create(ApplicationScope.ORDER, "save10", null), EMPLOYEE));

        assertEquals(PromotionErrorCode.DISCOUNT_CODE_ALREADY_EXISTS, ex.getErrorCode());
        verify(discountRepository, never()).save(any());
    }

    @Test
    void aCategoryThatCatalogDoesNotKnowRejectsTheDiscount() {
        org.mockito.Mockito.doThrow(new BusinessException(PromotionErrorCode.INVALID_DISCOUNT_CATEGORY))
                .when(categoryChecker).requireInUse(any());

        assertEquals(PromotionErrorCode.INVALID_DISCOUNT_CATEGORY, code(assertThrows(BusinessException.class,
                () -> service.create(create(ApplicationScope.CATEGORY, null, Set.of(CATEGORY_A)), EMPLOYEE))));
        verify(discountRepository, never()).save(any());
    }

    // ---- UC-ADM-DIS-003 edit ----

    @Test
    void updateReplacesTheDiscountAndItsTargets() {
        Discount existing = running();
        when(discountRepository.lockById(DISCOUNT_ID)).thenReturn(Optional.of(existing));

        DiscountResponse response = service.update(DISCOUNT_ID, update(ApplicationScope.CATEGORY, "", Set.of(CATEGORY_B)), EMPLOYEE);

        assertEquals("Edited", existing.getTitle());
        assertEquals(DiscountType.FIXED, existing.getDiscountType());
        assertEquals(ApplicationScope.CATEGORY, existing.getApplicationScope());
        assertNull(existing.getCode());
        assertEquals(EMPLOYEE, existing.getUpdatedBy());
        verify(categoryRepository).deleteByDiscountId(DISCOUNT_ID);
        verify(categoryRepository).saveAll(anyCollection());
        assertEquals(Set.of(CATEGORY_B), response.categoryIds());
    }

    @Test
    void anOmittedCodeKeepsTheCurrentOne() {
        Discount existing = running();
        when(discountRepository.lockById(DISCOUNT_ID)).thenReturn(Optional.of(existing));

        service.update(DISCOUNT_ID, update(ApplicationScope.ORDER, null, null), EMPLOYEE);

        assertEquals("OLD", existing.getCode());
    }

    @Test
    void keepingTheCurrentCodeWhileLeavingTheOrderScopeIsRejectedAndChangesNothing() {
        Discount existing = running();
        when(discountRepository.lockById(DISCOUNT_ID)).thenReturn(Optional.of(existing));

        assertEquals(PromotionErrorCode.INVALID_DISCOUNT_CODE, code(assertThrows(BusinessException.class,
                () -> service.update(DISCOUNT_ID, update(ApplicationScope.ALL_ITEMS, null, null), EMPLOYEE))));

        assertEquals("Old", existing.getTitle());
        verify(categoryRepository, never()).deleteByDiscountId(any());
    }

    @Test
    void updateRejectsACodeUsedByAnotherDiscount() {
        when(discountRepository.lockById(DISCOUNT_ID)).thenReturn(Optional.of(running()));
        when(discountRepository.existsByCodeIgnoreCaseAndIdNotAndStatusNot("TAKEN", DISCOUNT_ID, DiscountStatus.DELETED)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> service.update(DISCOUNT_ID, update(ApplicationScope.ORDER, "taken", null), EMPLOYEE));
    }

    @Test
    void updateOfADeletedOrUnknownDiscountIsNotFound() {
        Discount deleted = discount(DiscountStatus.DELETED, now, now.plusSeconds(60));
        when(discountRepository.lockById(DISCOUNT_ID)).thenReturn(Optional.of(deleted));

        assertThrows(ResourceNotFoundException.class,
                () -> service.update(DISCOUNT_ID, update(ApplicationScope.ORDER, null, null), EMPLOYEE));
        assertThrows(ResourceNotFoundException.class,
                () -> service.update(UUID.randomUUID(), update(ApplicationScope.ORDER, null, null), EMPLOYEE));
    }

    // ---- UC-ADM-DIS-001 list ----

    @Test
    void listShowsTheStateOfEachDiscountAndLoadsTargetsInOneQuery() {
        Discount expired = discount(DiscountStatus.ACTIVE, now.minus(5, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        expired.setId(UUID.randomUUID());
        Discount scheduled = discount(DiscountStatus.ACTIVE, now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS));
        scheduled.setId(UUID.randomUUID());
        Discount locked = discount(DiscountStatus.INACTIVE, now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS));
        locked.setId(UUID.randomUUID());
        Discount running = running();
        when(discountRepository.search(any(), any(), any())).thenReturn(
                new PageImpl<>(List.of(expired, scheduled, locked, running), PageRequest.of(0, 20), 4));
        when(categoryRepository.findByDiscountIdIn(anyCollection())).thenReturn(List.of());

        var page = service.list(0, 20, null);

        assertEquals(List.of(DiscountState.EXPIRED, DiscountState.SCHEDULED, DiscountState.LOCKED, DiscountState.RUNNING),
                page.getContent().stream().map(DiscountResponse::state).toList());
        verify(categoryRepository).findByDiscountIdIn(anyCollection());
    }

    @Test
    void listPassesTheStateFilterAndRejectsBadPaging() {
        when(discountRepository.search(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.list(0, 20, DiscountState.EXPIRED);

        verify(discountRepository).search(eq("EXPIRED"), any(), any());
        for (int[] paging : new int[][]{{-1, 20}, {0, 0}, {0, 101}}) {
            assertThrows(BusinessException.class, () -> service.list(paging[0], paging[1], null));
        }
    }

    @Test
    void getHidesDeletedDiscounts() {
        when(discountRepository.findByIdAndStatusNot(DISCOUNT_ID, DiscountStatus.DELETED)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(DISCOUNT_ID));
    }

    // ---- UC-ADM-DIS-005 lock ----

    @Test
    void locksAndUnlocksADiscount() {
        Discount existing = running();
        when(discountRepository.lockById(DISCOUNT_ID)).thenReturn(Optional.of(existing));

        assertEquals(DiscountState.LOCKED, service.updateStatus(DISCOUNT_ID, DiscountStatus.INACTIVE, EMPLOYEE).state());
        assertEquals(DiscountStatus.INACTIVE, existing.getStatus());
        assertEquals(EMPLOYEE, existing.getUpdatedBy());

        assertEquals(DiscountState.RUNNING, service.updateStatus(DISCOUNT_ID, DiscountStatus.ACTIVE, EMPLOYEE).state());
    }

    @Test
    void statusCannotBeSetToDeleted() {
        assertEquals(PromotionErrorCode.INVALID_DISCOUNT_STATUS, code(assertThrows(BusinessException.class,
                () -> service.updateStatus(DISCOUNT_ID, DiscountStatus.DELETED, EMPLOYEE))));
        verify(discountRepository, never()).lockById(any());
    }

    // ---- UC-ADM-DIS-004 delete ----

    @Test
    void aRunningDiscountCannotBeDeleted() {
        Discount existing = running();
        when(discountRepository.lockById(DISCOUNT_ID)).thenReturn(Optional.of(existing));

        assertEquals(PromotionErrorCode.DISCOUNT_RUNNING,
                code(assertThrows(BusinessException.class, () -> service.delete(DISCOUNT_ID, EMPLOYEE))));
        assertEquals(DiscountStatus.ACTIVE, existing.getStatus());
    }

    @Test
    void lockedScheduledAndExpiredDiscountsCanBeDeleted() {
        Discount[] deletable = {
                discount(DiscountStatus.INACTIVE, now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS)),
                discount(DiscountStatus.ACTIVE, now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS)),
                discount(DiscountStatus.ACTIVE, now.minus(5, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS))};
        for (Discount existing : deletable) {
            when(discountRepository.lockById(DISCOUNT_ID)).thenReturn(Optional.of(existing));

            service.delete(DISCOUNT_ID, EMPLOYEE);

            assertEquals(DiscountStatus.DELETED, existing.getStatus());
            assertEquals(EMPLOYEE, existing.getUpdatedBy());
        }
    }

    @Test
    void deletingAnAlreadyDeletedDiscountIsNotFound() {
        when(discountRepository.lockById(DISCOUNT_ID)).thenReturn(Optional.of(discount(DiscountStatus.DELETED, now, now.plusSeconds(60))));

        assertThrows(ResourceNotFoundException.class, () -> service.delete(DISCOUNT_ID, EMPLOYEE));
    }
}
