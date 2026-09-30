package com.ecm.catalog.repository;

import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("SELECT c FROM Category c WHERE c.status = :status ORDER BY c.name ASC")
    List<Category> findByStatus(@Param("status") CatalogStatus status);

    @Query("SELECT c FROM Category c WHERE c.parentId IS NULL AND c.status = :status ORDER BY c.name ASC")
    List<Category> findRootCategoriesByStatus(@Param("status") CatalogStatus status);

    @Query("SELECT c FROM Category c WHERE c.id = :id AND c.status = :status")
    Optional<Category> findByIdAndStatus(@Param("id") UUID id, @Param("status") CatalogStatus status);

    boolean existsBySeoName(String seoName);
}
