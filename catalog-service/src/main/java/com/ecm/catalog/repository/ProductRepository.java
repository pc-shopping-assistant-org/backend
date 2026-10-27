package com.ecm.catalog.repository;

import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Product;
import org.springframework.data.domain.Page;
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

    String SEARCH_PRODUCTS = """
            SELECT p FROM Product p
            WHERE p.status IN :statuses
              AND (:cursor IS NULL OR p.id < :cursor)
              AND (:anyCategory = TRUE OR p.categoryId IN :categoryIds)
              AND (:brandId IS NULL OR p.brandId = :brandId)
              AND (:keyword IS NULL OR LOWER(p.name) LIKE :keyword
                   OR LOWER(p.seoName) LIKE :keyword
                   OR LOWER(p.description) LIKE :keyword)
              AND (:onlyWithVariants = FALSE OR EXISTS (
                   SELECT x.id FROM ProductVariant x WHERE x.productId = p.id AND x.status IN :variantStatuses))
              AND ((:minPrice IS NULL AND :maxPrice IS NULL) OR EXISTS (
                   SELECT v.id FROM ProductVariant v WHERE v.productId = p.id
                     AND v.status IN :variantStatuses
                     AND (:minPrice IS NULL OR v.price >= :minPrice)
                     AND (:maxPrice IS NULL OR v.price <= :maxPrice)))
            ORDER BY p.id DESC
            """;

    /**
     * Keyset page (newest first) of products whose status is one of {@code statuses}. A null cursor starts at the
     * newest product. Unless {@code anyCategory}, only products in {@code categoryIds} match. Price bounds match when at least one variant, with a status in {@code variantStatuses},
     * falls inside them. With {@code onlyWithVariants}, products without a variant in {@code variantStatuses} are skipped
     * by the query itself so a page is never short of the requested size.
     */
    @Query(SEARCH_PRODUCTS)
    List<Product> search(
            @Param("statuses") Collection<CatalogStatus> statuses,
            @Param("variantStatuses") Collection<CatalogStatus> variantStatuses,
            @Param("onlyWithVariants") boolean onlyWithVariants,
            @Param("cursor") UUID cursor,
            @Param("anyCategory") boolean anyCategory,
            @Param("categoryIds") Collection<UUID> categoryIds,
            @Param("brandId") UUID brandId,
            @Param("keyword") String keyword,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            Pageable pageable
    );

    /** The same products as {@link #search}, but a numbered page (the cursor is ignored) with the total the storefront needs to number them. */
    @Query(SEARCH_PRODUCTS)
    Page<Product> searchPage(
            @Param("statuses") Collection<CatalogStatus> statuses,
            @Param("variantStatuses") Collection<CatalogStatus> variantStatuses,
            @Param("onlyWithVariants") boolean onlyWithVariants,
            @Param("cursor") UUID cursor,
            @Param("anyCategory") boolean anyCategory,
            @Param("categoryIds") Collection<UUID> categoryIds,
            @Param("brandId") UUID brandId,
            @Param("keyword") String keyword,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            Pageable pageable
    );

    /**
     * How many products the storefront lists in each category: active, with at least one variant in
     * {@code variantStatus}, the same rule the public product list applies. Categories without any are absent.
     */
    @Query("""
            SELECT new com.ecm.catalog.repository.CategoryProductCount(p.categoryId, COUNT(p))
            FROM Product p
            WHERE p.status = :status
              AND EXISTS (SELECT x.id FROM ProductVariant x WHERE x.productId = p.id AND x.status = :variantStatus)
            GROUP BY p.categoryId
            """)
    List<CategoryProductCount> countListedByCategory(
            @Param("status") CatalogStatus status, @Param("variantStatus") CatalogStatus variantStatus);

    boolean existsByCategoryIdAndStatusNot(UUID categoryId, CatalogStatus status);

    boolean existsByBrandIdAndStatusNot(UUID brandId, CatalogStatus status);
}
