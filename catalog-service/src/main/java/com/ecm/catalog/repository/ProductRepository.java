package com.ecm.catalog.repository;

import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySeoNameAndStatusNot(String seoName, CatalogStatus status);

    boolean existsBySeoNameAndIdNotAndStatusNot(String seoName, UUID id, CatalogStatus status);

    Optional<Product> findBySeoNameAndStatus(String seoName, CatalogStatus status);

    Optional<Product> findByIdAndStatusIn(UUID id, Collection<CatalogStatus> statuses);

    /**
     * Keyset page (newest first) of products whose status is one of {@code statuses}. A null cursor starts at the
     * newest product. Price bounds match when at least one variant, with a status in {@code variantStatuses},
     * falls inside them.
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.status IN :statuses
              AND (:cursor IS NULL OR p.id < :cursor)
              AND (:categoryId IS NULL OR p.categoryId = :categoryId)
              AND (:brandId IS NULL OR p.brandId = :brandId)
              AND (:keyword IS NULL OR LOWER(p.name) LIKE :keyword
                   OR LOWER(p.seoName) LIKE :keyword
                   OR LOWER(p.description) LIKE :keyword)
              AND ((:minPrice IS NULL AND :maxPrice IS NULL) OR EXISTS (
                   SELECT v.id FROM ProductVariant v WHERE v.productId = p.id
                     AND v.status IN :variantStatuses
                     AND (:minPrice IS NULL OR v.price >= :minPrice)
                     AND (:maxPrice IS NULL OR v.price <= :maxPrice)))
            ORDER BY p.id DESC
            """)
    List<Product> search(
            @Param("statuses") Collection<CatalogStatus> statuses,
            @Param("variantStatuses") Collection<CatalogStatus> variantStatuses,
            @Param("cursor") UUID cursor,
            @Param("categoryId") UUID categoryId,
            @Param("brandId") UUID brandId,
            @Param("keyword") String keyword,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            Pageable pageable
    );

    boolean existsByCategoryIdAndStatusNot(UUID categoryId, CatalogStatus status);

    boolean existsByBrandIdAndStatusNot(UUID brandId, CatalogStatus status);
}
