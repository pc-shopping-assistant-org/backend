package com.ecm.promotion.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.DuplicateResourceException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.PageResponse;
import com.ecm.promotion.dto.request.CreateDiscountRequest;
import com.ecm.promotion.dto.request.UpdateDiscountRequest;
import com.ecm.promotion.dto.response.DiscountResponse;
import com.ecm.promotion.entity.ApplicationScope;
import com.ecm.promotion.entity.Discount;
import com.ecm.promotion.entity.DiscountCategory;
import com.ecm.promotion.entity.DiscountState;
import com.ecm.promotion.entity.DiscountStatus;
import com.ecm.promotion.exception.PromotionErrorCode;
import com.ecm.promotion.mapper.DiscountMapper;
import com.ecm.promotion.repository.DiscountCategoryRepository;
import com.ecm.promotion.repository.DiscountRepository;
import com.ecm.promotion.util.DiscountRules;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Employee-facing discount management: UC-ADM-DIS-001..005. */
@Service
@RequiredArgsConstructor
public class DiscountService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DiscountRepository discountRepository;
    private final DiscountCategoryRepository categoryRepository;
    private final DiscountCategoryChecker categoryChecker;
    private final DiscountMapper discountMapper;

    @Transactional
    public DiscountResponse create(CreateDiscountRequest request, UUID employeeId) {
        // 1. Validate the definition and the code, and that the targets fit the scope
        String code = DiscountRules.normalizeCode(request.code());
        DiscountRules.validateDefinition(request.discountType(), request.value(), request.minOrderAmount(), request.startAt(), request.endAt());
        DiscountRules.validateCode(code, request.applicationScope());
        Set<UUID> categoryIds = validateTargets(request.applicationScope(), request.categoryIds());
        if (code != null && discountRepository.existsByCodeIgnoreCaseAndStatusNot(code, DiscountStatus.DELETED)) {
            throw new DuplicateResourceException(PromotionErrorCode.DISCOUNT_CODE_ALREADY_EXISTS, "Discount", "code", code);
        }

        // 2. Save the discount with all its targets in one transaction
        Discount discount = discountRepository.save(Discount.builder()
                .code(code).title(request.title().trim()).discountType(request.discountType()).value(request.value())
                .applicationScope(request.applicationScope()).minOrderAmount(DiscountRules.minOrderAmount(request.minOrderAmount()))
                .startAt(request.startAt()).endAt(request.endAt()).description(request.description())
                .createdBy(employeeId).status(DiscountStatus.ACTIVE).build());
        saveTargets(discount.getId(), categoryIds);
        return toResponse(discount, categoryIds);
    }

    @Transactional
    public DiscountResponse update(UUID id, UpdateDiscountRequest request, UUID employeeId) {
        // 1. Validate the new definition
        DiscountRules.validateDefinition(request.discountType(), request.value(), request.minOrderAmount(), request.startAt(), request.endAt());
        Set<UUID> categoryIds = validateTargets(request.applicationScope(), request.categoryIds());

        // 2. Resolve the code: omitted keeps the current one, blank removes it
        Discount discount = lockLive(id);
        String code = request.code() == null ? discount.getCode() : DiscountRules.normalizeCode(request.code());
        DiscountRules.validateCode(code, request.applicationScope());
        if (code != null && discountRepository.existsByCodeIgnoreCaseAndIdNotAndStatusNot(code, id, DiscountStatus.DELETED)) {
            throw new DuplicateResourceException(PromotionErrorCode.DISCOUNT_CODE_ALREADY_EXISTS, "Discount", "code", code);
        }

        // 3. Replace the discount and its targets together
        discount.setCode(code);
        discount.setTitle(request.title().trim());
        discount.setDiscountType(request.discountType());
        discount.setValue(request.value());
        discount.setApplicationScope(request.applicationScope());
        discount.setMinOrderAmount(DiscountRules.minOrderAmount(request.minOrderAmount()));
        discount.setStartAt(request.startAt());
        discount.setEndAt(request.endAt());
        discount.setDescription(request.description());
        discount.setUpdatedBy(employeeId);
        categoryRepository.deleteByDiscountId(id);
        categoryRepository.flush();
        saveTargets(id, categoryIds);
        return toResponse(discountRepository.save(discount), categoryIds);
    }

    @Transactional(readOnly = true)
    public PageResponse<DiscountResponse> list(int page, int size, DiscountState state) {
        // 1. Validate the paging bounds
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Invalid pagination bounds");
        }

        // 2. Load the page, then all of its targets in one query
        Instant now = Instant.now();
        Page<Discount> result = discountRepository.search(state == null ? null : state.name(), now,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, Set<UUID>> targets = categoryRepository
                .findByDiscountIdIn(result.getContent().stream().map(Discount::getId).toList()).stream()
                .collect(Collectors.groupingBy(DiscountCategory::getDiscountId,
                        Collectors.mapping(DiscountCategory::getCategoryId, Collectors.toSet())));
        return PageResponse.of(result.map(discount ->
                discountMapper.toResponse(discount, discount.stateAt(now), targets.getOrDefault(discount.getId(), Set.of()))));
    }

    @Transactional(readOnly = true)
    public DiscountResponse get(UUID id) {
        Discount discount = discountRepository.findByIdAndStatusNot(id, DiscountStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Discount", id));
        return toResponse(discount, targetsOf(id));
    }

    @Transactional
    public DiscountResponse updateStatus(UUID id, DiscountStatus status, UUID employeeId) {
        // 1. Deleting has its own endpoint and rule
        if (status == DiscountStatus.DELETED) {
            throw new BusinessException(PromotionErrorCode.INVALID_DISCOUNT_STATUS);
        }

        // 2. Lock or unlock
        Discount discount = lockLive(id);
        discount.setStatus(status);
        discount.setUpdatedBy(employeeId);
        return toResponse(discountRepository.save(discount), targetsOf(id));
    }

    @Transactional
    public void delete(UUID id, UUID employeeId) {
        // 1. A discount that is in effect right now must be locked first
        Discount discount = lockLive(id);
        if (discount.stateAt(Instant.now()) == DiscountState.RUNNING) {
            throw new BusinessException(PromotionErrorCode.DISCOUNT_RUNNING);
        }

        // 2. Soft delete: finished orders keep the discount amount they were charged
        discount.setStatus(DiscountStatus.DELETED);
        discount.setUpdatedBy(employeeId);
        discountRepository.save(discount);
    }

    private Discount lockLive(UUID id) {
        Discount discount = discountRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Discount", id));
        if (discount.getStatus() == DiscountStatus.DELETED) {
            throw new ResourceNotFoundException("Discount", id);
        }
        return discount;
    }

    /** ORDER and ALL_ITEMS take no targets; CATEGORY needs at least one, and every one must be in use. */
    private Set<UUID> validateTargets(ApplicationScope scope, Set<UUID> categoryIds) {
        Set<UUID> targets = categoryIds == null ? Set.of() : categoryIds;
        if (scope == ApplicationScope.CATEGORY) {
            if (targets.isEmpty()) {
                throw new BusinessException(PromotionErrorCode.INVALID_DISCOUNT_TARGETS);
            }
            categoryChecker.requireInUse(targets);
            return targets;
        }
        if (!targets.isEmpty()) {
            throw new BusinessException(PromotionErrorCode.INVALID_DISCOUNT_TARGETS);
        }
        return targets;
    }

    private void saveTargets(UUID discountId, Set<UUID> categoryIds) {
        categoryRepository.saveAll(categoryIds.stream()
                .map(categoryId -> DiscountCategory.builder().discountId(discountId).categoryId(categoryId).build()).toList());
    }

    private Set<UUID> targetsOf(UUID discountId) {
        return categoryRepository.findByDiscountIdIn(List.of(discountId)).stream()
                .map(DiscountCategory::getCategoryId).collect(Collectors.toSet());
    }

    private DiscountResponse toResponse(Discount discount, Set<UUID> categoryIds) {
        return discountMapper.toResponse(discount, discount.stateAt(Instant.now()), categoryIds);
    }
}
