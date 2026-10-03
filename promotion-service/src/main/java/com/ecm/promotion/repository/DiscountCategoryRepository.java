package com.ecm.promotion.repository;

import com.ecm.promotion.entity.DiscountCategory;
import com.ecm.promotion.entity.DiscountCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DiscountCategoryRepository extends JpaRepository<DiscountCategory, DiscountCategoryId> {
    List<DiscountCategory> findByDiscountId(UUID discountId);
    List<DiscountCategory> findByCategoryIdIn(java.util.Collection<UUID> categoryIds);
    List<DiscountCategory> findByDiscountIdIn(java.util.Collection<UUID> ids);
    void deleteByDiscountId(UUID discountId);
}
