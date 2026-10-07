package com.ecm.promotion.repository;

import com.ecm.promotion.entity.DiscountCategory;
import com.ecm.promotion.entity.DiscountCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DiscountCategoryRepository extends JpaRepository<DiscountCategory, DiscountCategoryId> {

    List<DiscountCategory> findByDiscountIdIn(Collection<UUID> discountIds);

    List<DiscountCategory> findByCategoryIdIn(Collection<UUID> categoryIds);

    void deleteByDiscountId(UUID discountId);
}
