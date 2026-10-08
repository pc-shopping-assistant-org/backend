package com.ecm.catalog.repository;

import com.ecm.catalog.entity.Brand;
import com.ecm.catalog.entity.CatalogStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BrandRepository extends JpaRepository<Brand, UUID> {

    @Query("SELECT b FROM Brand b WHERE b.status = :status ORDER BY b.name ASC")
    List<Brand> findByStatus(@Param("status") CatalogStatus status);

    @Query("SELECT b FROM Brand b WHERE b.id = :id AND b.status = :status")
    Optional<Brand> findByIdAndStatus(@Param("id") UUID id, @Param("status") CatalogStatus status);

    @Query("SELECT b FROM Brand b WHERE b.status <> :status ORDER BY b.name ASC")
    List<Brand> findByStatusNot(@Param("status") CatalogStatus status);

    boolean existsBySeoNameAndStatusNot(String seoName, CatalogStatus status);

    boolean existsByNameIgnoreCaseAndStatusNot(String name, CatalogStatus status);

    boolean existsByIdAndStatus(UUID id, CatalogStatus status);
}
