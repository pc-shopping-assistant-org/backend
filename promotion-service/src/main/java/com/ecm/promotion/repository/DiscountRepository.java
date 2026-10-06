package com.ecm.promotion.repository;

import com.ecm.promotion.entity.Discount;
import com.ecm.promotion.entity.DiscountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscountRepository extends JpaRepository<Discount, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Discount d where d.id = :id")
    Optional<Discount> lockById(@Param("id") UUID id);

    Optional<Discount> findByIdAndStatusNot(UUID id, DiscountStatus status);

    Optional<Discount> findByCodeIgnoreCaseAndStatusNot(String code, DiscountStatus status);

    boolean existsByCodeIgnoreCaseAndStatusNot(String code, DiscountStatus status);

    boolean existsByCodeIgnoreCaseAndIdNotAndStatusNot(String code, UUID id, DiscountStatus status);

    @Query("""
            select d from Discount d
            where d.applicationScope = com.ecm.promotion.entity.ApplicationScope.ALL_ITEMS
              and d.status = :status and d.startAt <= :now and d.endAt > :now
            """)
    List<Discount> findActiveAllItems(@Param("status") DiscountStatus status, @Param("now") Instant now);

    /** ORDER discounts without a code, which apply to a cart on their own while they are in effect. */
    @Query("""
            select d from Discount d
            where d.applicationScope = com.ecm.promotion.entity.ApplicationScope.ORDER and d.code is null
              and d.status = :status and d.startAt <= :now and d.endAt > :now
            """)
    List<Discount> findActiveAutomaticOrder(@Param("status") DiscountStatus status, @Param("now") Instant now);

    List<Discount> findByIdIn(Collection<UUID> ids);

    /**
     * Non-deleted discounts, newest first. A null {@code state} (LOCKED, SCHEDULED, RUNNING or EXPIRED) lists every
     * one of them; a locked discount is LOCKED whatever its dates, so the other states only match ACTIVE rows.
     */
    @Query("""
            select d from Discount d
            where d.status <> com.ecm.promotion.entity.DiscountStatus.DELETED
              and (:state is null
                   or (:state = 'LOCKED' and d.status = com.ecm.promotion.entity.DiscountStatus.INACTIVE)
                   or (:state = 'SCHEDULED' and d.status = com.ecm.promotion.entity.DiscountStatus.ACTIVE and d.startAt > :now)
                   or (:state = 'RUNNING' and d.status = com.ecm.promotion.entity.DiscountStatus.ACTIVE
                       and d.startAt <= :now and d.endAt > :now)
                   or (:state = 'EXPIRED' and d.status = com.ecm.promotion.entity.DiscountStatus.ACTIVE and d.endAt <= :now))
            """)
    Page<Discount> search(@Param("state") String state, @Param("now") Instant now, Pageable pageable);
}
