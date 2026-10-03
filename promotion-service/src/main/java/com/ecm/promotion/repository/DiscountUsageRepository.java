package com.ecm.promotion.repository;

import com.ecm.promotion.entity.DiscountUsage;
import com.ecm.promotion.entity.DiscountUsageId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DiscountUsageRepository extends JpaRepository<DiscountUsage, DiscountUsageId> {
    long countByDiscountId(UUID discountId);
}
