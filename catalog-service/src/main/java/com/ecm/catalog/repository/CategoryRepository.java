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
import java.util.Collection;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.hardwareType WHERE c.status = :status ORDER BY c.displayOrder ASC, c.name ASC")
    List<Category> findByStatus(@Param("status") CatalogStatus status);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.hardwareType WHERE c.parentId IS NULL AND c.status = :status ORDER BY c.displayOrder ASC, c.name ASC")
    List<Category> findRootCategoriesByStatus(@Param("status") CatalogStatus status);

    @Query("SELECT c FROM Category c WHERE c.id = :id AND c.status = :status")
    Optional<Category> findByIdAndStatus(@Param("id") UUID id, @Param("status") CatalogStatus status);

    boolean existsByIdAndStatus(UUID id, CatalogStatus status);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.hardwareType WHERE c.status <> :status ORDER BY c.displayOrder ASC, c.name ASC")
    List<Category> findByStatusNot(@Param("status") CatalogStatus status);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.hardwareType WHERE c.id IN :ids")
    List<Category> findAllWithHardwareTypeByIdIn(@Param("ids") Collection<UUID> ids);

    boolean existsBySeoNameAndStatusNot(String seoName, CatalogStatus status);

    boolean existsByNameIgnoreCaseAndStatusNot(String name, CatalogStatus status);

    boolean existsByParentIdAndStatusNot(UUID parentId, CatalogStatus status);

    /** The category and its live ancestors, the root first; empty when the category does not exist. */
    @Query(value = """
            WITH RECURSIVE chain AS (
                SELECT id, parent_id, 0 AS depth FROM categories WHERE id = :id AND status <> 'DELETED'
                UNION ALL
                SELECT c.id, c.parent_id, ch.depth + 1 FROM categories c JOIN chain ch ON c.id = ch.parent_id
                WHERE c.status <> 'DELETED' AND ch.depth < 32
            )
            SELECT id FROM chain ORDER BY depth DESC
            """, nativeQuery = true)
    List<UUID> findSelfAndAncestorIds(@Param("id") UUID id);

    /** The category with the given id and all its live descendants; empty when the category does not exist. */
    @Query(value = """
            WITH RECURSIVE tree AS (
                SELECT id FROM categories WHERE id = :id AND status <> 'DELETED'
                UNION ALL
                SELECT c.id FROM categories c JOIN tree t ON c.parent_id = t.id WHERE c.status <> 'DELETED'
            )
            SELECT id FROM tree
            """, nativeQuery = true)
    List<UUID> findSelfAndDescendantIds(@Param("id") UUID id);
}
