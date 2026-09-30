package com.ecm.catalog.repository;

import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySeoName(String seoName);

    boolean existsByIdAndStatus(UUID id, CatalogStatus status);

    Optional<Product> findBySeoNameAndStatus(String seoName, CatalogStatus status);

    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.status = :status")
    Optional<Product> findByIdAndStatus(@Param("id") UUID id, @Param("status") CatalogStatus status);

    /**
     * Initial page for public catalog with filters.
     * Returns products ordered by ID DESC for cursor pagination.
     */
    @Query("""
            SELECT DISTINCT p FROM Product p
            WHERE p.status = :status
              AND (:categoryId IS NULL OR p.categoryId = :categoryId)
              AND (:brandId IS NULL OR p.brandId = :brandId)
              AND (:keyword IS NULL OR LOWER(p.name) LIKE :keyword
                   OR LOWER(p.seoName) LIKE :keyword
                   OR LOWER(COALESCE(p.description, '')) LIKE :keyword)
              AND ((:minPrice IS NULL AND :maxPrice IS NULL) OR EXISTS (
                   SELECT v.id FROM ProductVariant v WHERE v.productId = p.id
                     AND v.status = :status
                     AND (:minPrice IS NULL OR v.listPrice >= :minPrice)
                     AND (:maxPrice IS NULL OR v.listPrice <= :maxPrice)))
            ORDER BY p.id DESC
            """)
    List<Product> findInitial(
            @Param("status") CatalogStatus status,
            @Param("categoryId") UUID categoryId,
            @Param("brandId") UUID brandId,
            @Param("keyword") String keyword,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            Pageable pageable
    );

    /**
     * Cursor page for public catalog with filters.
     * Continues from the given cursor UUID.
     */
    @Query("""
            SELECT DISTINCT p FROM Product p
            WHERE p.status = :status
              AND p.id < :cursor
              AND (:categoryId IS NULL OR p.categoryId = :categoryId)
              AND (:brandId IS NULL OR p.brandId = :brandId)
              AND (:keyword IS NULL OR LOWER(p.name) LIKE :keyword
                   OR LOWER(p.seoName) LIKE :keyword
                   OR LOWER(COALESCE(p.description, '')) LIKE :keyword)
              AND ((:minPrice IS NULL AND :maxPrice IS NULL) OR EXISTS (
                   SELECT v.id FROM ProductVariant v WHERE v.productId = p.id
                     AND v.status = :status
                     AND (:minPrice IS NULL OR v.listPrice >= :minPrice)
                     AND (:maxPrice IS NULL OR v.listPrice <= :maxPrice)))
            ORDER BY p.id DESC
            """)
    List<Product> findAfterCursor(
            @Param("status") CatalogStatus status,
            @Param("cursor") UUID cursor,
            @Param("categoryId") UUID categoryId,
            @Param("brandId") UUID brandId,
            @Param("keyword") String keyword,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            Pageable pageable
    );

    long countByCategoryId(UUID categoryId);

    long countByBrandId(UUID brandId);
}
