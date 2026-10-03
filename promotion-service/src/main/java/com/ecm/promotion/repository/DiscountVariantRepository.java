package com.ecm.promotion.repository;

import com.ecm.promotion.entity.DiscountVariant;
import com.ecm.promotion.entity.DiscountVariantId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DiscountVariantRepository extends JpaRepository<DiscountVariant, DiscountVariantId> {
    List<DiscountVariant> findByDiscountId(UUID discountId);
    List<DiscountVariant> findByVariantIdIn(Collection<UUID> variantIds);
    List<DiscountVariant> findByDiscountIdIn(java.util.Collection<UUID> ids);
    void deleteByDiscountId(UUID discountId);
}
