package com.ecm.catalog.repository;

import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    @Query("SELECT v FROM ProductVariant v WHERE v.productId IN :productIds AND v.status = :status ORDER BY v.productId, v.listPrice")
    List<ProductVariant> findByProductIdInAndStatus(@Param("productIds") List<UUID> productIds,
                                                     @Param("status") CatalogStatus status);

    @Query("SELECT v FROM ProductVariant v WHERE v.productId = :productId AND v.status = :status")
    List<ProductVariant> findByProductIdAndStatus(@Param("productId") UUID productId,
                                                   @Param("status") CatalogStatus status);

    @Query("SELECT v FROM ProductVariant v WHERE v.id = :id AND v.status = :status")
    Optional<ProductVariant> findByIdAndStatus(@Param("id") UUID id, @Param("status") CatalogStatus status);

    boolean existsBySku(String sku);
}
