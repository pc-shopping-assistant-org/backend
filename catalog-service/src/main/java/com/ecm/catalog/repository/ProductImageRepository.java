package com.ecm.catalog.repository;

import com.ecm.catalog.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    @Query("SELECT pi FROM ProductImage pi WHERE pi.productId IN :productIds ORDER BY pi.productId, pi.isMain DESC, pi.createdAt ASC, pi.id ASC")
    List<ProductImage> findByProductIdIn(@Param("productIds") Collection<UUID> productIds);

    @Query("SELECT pi FROM ProductImage pi WHERE pi.productId IN :productIds AND pi.isMain = true")
    List<ProductImage> findMainByProductIdIn(@Param("productIds") Collection<UUID> productIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ProductImage pi WHERE pi.productId = :productId")
    void deleteByProductId(@Param("productId") UUID productId);
}
