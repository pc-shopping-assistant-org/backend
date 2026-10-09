package com.ecm.catalog.repository;

import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
public interface ProductReviewRepository extends JpaRepository<ProductReview, UUID> {

    boolean existsByOrderItemId(UUID orderItemId);

    Optional<ProductReview> findByIdAndProductIdAndCustomerIdAndStatus(UUID id, UUID productId, UUID customerId, CatalogStatus status);

    Page<ProductReview> findByProductIdAndStatusOrderByCreatedAtDesc(UUID productId, CatalogStatus status, Pageable pageable);

    List<ProductReview> findByCustomerIdAndOrderItemIdInAndStatus(UUID customerId, Collection<UUID> orderItemIds, CatalogStatus status);

    List<ProductReview> findByProductIdAndCustomerIdAndStatusOrderByCreatedAtDesc(UUID productId, UUID customerId, CatalogStatus status);

    /**
     * Spends the single edit; zero rows means another request already did, so two concurrent edits cannot both succeed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProductReview r SET r.rating = :rating, r.comment = :comment, r.editedAt = :editedAt " +
            "WHERE r.id = :id AND r.customerId = :customerId AND r.editedAt IS NULL")
    int applyEdit(@Param("id") UUID id, @Param("customerId") UUID customerId, @Param("rating") int rating,
                  @Param("comment") String comment, @Param("editedAt") Instant editedAt);
}
