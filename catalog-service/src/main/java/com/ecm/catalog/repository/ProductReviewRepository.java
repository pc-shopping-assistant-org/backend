package com.ecm.catalog.repository;

import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, UUID> {

    boolean existsByOrderItemId(UUID orderItemId);

    Page<ProductReview> findByProductIdAndStatusOrderByCreatedAtDesc(UUID productId, CatalogStatus status, Pageable pageable);
}
