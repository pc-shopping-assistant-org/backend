package com.ecm.promotion.repository;

import com.ecm.promotion.entity.Discount;
import com.ecm.promotion.entity.DiscountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscountRepository extends JpaRepository<Discount, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Discount d where d.id = :id")
    Optional<Discount> lockById(@Param("id") UUID id);

    Optional<Discount> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);

    @Query("select d from Discount d where d.applicationScope = com.ecm.promotion.entity.ApplicationScope.ALL_ITEMS and d.status = :status and d.startAt <= :now and d.endAt > :now")
    List<Discount> findActiveAllItems(@Param("status") DiscountStatus status, @Param("now") Instant now);

    List<Discount> findByIdIn(Collection<UUID> ids);
}
