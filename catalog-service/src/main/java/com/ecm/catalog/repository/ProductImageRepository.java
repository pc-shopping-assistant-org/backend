package com.ecm.catalog.repository;

import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    @Query("SELECT pi FROM ProductImage pi WHERE pi.productVariantId = :variantId AND pi.status = :status ORDER BY pi.isMain DESC, pi.createdAt ASC")
    List<ProductImage> findByProductVariantIdAndStatus(@Param("variantId") UUID variantId,
                                                        @Param("status") CatalogStatus status);

    @Query("SELECT pi FROM ProductImage pi WHERE pi.productVariantId IN :variantIds AND pi.status = :status ORDER BY pi.isMain DESC")
    List<ProductImage> findByProductVariantIdInAndStatus(@Param("variantIds") List<UUID> variantIds,
                                                          @Param("status") CatalogStatus status);
}
