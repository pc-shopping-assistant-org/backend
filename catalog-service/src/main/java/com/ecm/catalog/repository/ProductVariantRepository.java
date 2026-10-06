package com.ecm.catalog.repository;

import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    @Query("SELECT v FROM ProductVariant v WHERE v.productId IN :productIds AND v.status IN :statuses ORDER BY v.productId, v.price")
    List<ProductVariant> findByProductIdInAndStatusIn(@Param("productIds") Collection<UUID> productIds,
                                                      @Param("statuses") Collection<CatalogStatus> statuses);

    @Query("SELECT v FROM ProductVariant v WHERE v.productId = :productId AND v.status IN :statuses ORDER BY v.price, v.id")
    List<ProductVariant> findByProductIdAndStatusIn(@Param("productId") UUID productId,
                                                    @Param("statuses") Collection<CatalogStatus> statuses);

    List<ProductVariant> findByProductIdAndStatusNot(UUID productId, CatalogStatus status);

    @Query("SELECT v FROM ProductVariant v WHERE v.id = :id AND v.status = :status")
    Optional<ProductVariant> findByIdAndStatus(@Param("id") UUID id, @Param("status") CatalogStatus status);

    boolean existsBySkuAndStatusNot(String sku, CatalogStatus status);

    boolean existsByBarcodeAndStatusNot(String barcode, CatalogStatus status);

    boolean existsBySkuAndIdNotAndStatusNot(String sku, UUID id, CatalogStatus status);

    boolean existsByBarcodeAndIdNotAndStatusNot(String barcode, UUID id, CatalogStatus status);
}
