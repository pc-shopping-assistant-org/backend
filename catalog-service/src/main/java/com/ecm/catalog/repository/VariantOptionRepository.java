package com.ecm.catalog.repository;

import com.ecm.catalog.entity.VariantOption;
import com.ecm.catalog.entity.VariantOptionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface VariantOptionRepository extends JpaRepository<VariantOption, VariantOptionId> {

    List<VariantOption> findByProductVariantIdIn(Collection<UUID> variantIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM VariantOption vo WHERE vo.productVariantId = :variantId")
    void deleteByProductVariantId(@Param("variantId") UUID variantId);
}
