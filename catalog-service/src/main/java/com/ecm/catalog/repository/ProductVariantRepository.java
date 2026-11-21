package com.ecm.catalog.repository;

import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    @Query("SELECT v FROM ProductVariant v WHERE v.productId IN :productIds AND v.status IN :statuses ORDER BY v.productId, v.price, v.id")
    List<ProductVariant> findByProductIdInAndStatusIn(@Param("productIds") Collection<UUID> productIds,
                                                      @Param("statuses") Collection<CatalogStatus> statuses);

    @Query("SELECT v FROM ProductVariant v WHERE v.productId = :productId AND v.status IN :statuses ORDER BY v.price, v.id")
    List<ProductVariant> findByProductIdAndStatusIn(@Param("productId") UUID productId,
                                                    @Param("statuses") Collection<CatalogStatus> statuses);

    List<ProductVariant> findByProductIdAndStatusNot(UUID productId, CatalogStatus status);

    @Query("SELECT v FROM ProductVariant v WHERE v.id = :id AND v.status = :status")
    Optional<ProductVariant> findByIdAndStatus(@Param("id") UUID id, @Param("status") CatalogStatus status);

    /** Locks the variants in id order, so two orders reserving overlapping variants cannot deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ProductVariant v WHERE v.id IN :ids ORDER BY v.id")
    List<ProductVariant> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);

    /** Locks the non-deleted variants of a product, so stock reservations wait while its variations are rewritten. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ProductVariant v WHERE v.productId = :productId AND v.status <> :excluded ORDER BY v.id")
    List<ProductVariant> findByProductIdForUpdate(@Param("productId") UUID productId, @Param("excluded") CatalogStatus excluded);

    /**
     * Adds {@code delta} to the stock in the database, not to a value read earlier, so a concurrent order is never undone.
     * Returns 0 when the stock would go below zero.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProductVariant v SET v.quantity = v.quantity + :delta, v.updatedAt = :now, v.updatedBy = :employeeId " +
            "WHERE v.id = :id AND v.quantity + :delta >= 0")
    int adjustQuantity(@Param("id") UUID id, @Param("delta") int delta, @Param("now") Instant now, @Param("employeeId") UUID employeeId);

    List<ProductVariant> findBySkuInAndStatusNot(Collection<String> skus, CatalogStatus status);

    List<ProductVariant> findByBarcodeInAndStatusNot(Collection<String> barcodes, CatalogStatus status);

    long countByStatusAndQuantityBetween(CatalogStatus status, int minQuantity, int maxQuantity);

    boolean existsBySkuAndStatusNot(String sku, CatalogStatus status);

    boolean existsByBarcodeAndStatusNot(String barcode, CatalogStatus status);

    boolean existsBySkuAndIdNotAndStatusNot(String sku, UUID id, CatalogStatus status);

    boolean existsByBarcodeAndIdNotAndStatusNot(String barcode, UUID id, CatalogStatus status);
}
