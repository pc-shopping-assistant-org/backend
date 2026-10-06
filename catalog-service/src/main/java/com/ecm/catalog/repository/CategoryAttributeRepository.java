package com.ecm.catalog.repository;

import com.ecm.catalog.entity.CategoryAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryAttributeRepository extends JpaRepository<CategoryAttribute, UUID> {

    List<CategoryAttribute> findByCategoryGroupIdInOrderByDisplayOrderAsc(Collection<UUID> categoryGroupIds);

    boolean existsByAttributeId(UUID attributeId);

    @Modifying
    @Query("DELETE FROM CategoryAttribute ca WHERE ca.categoryGroupId IN "
            + "(SELECT g.id FROM CategoryAttributeGroup g WHERE g.categoryId = :categoryId)")
    void deleteByCategoryId(@Param("categoryId") UUID categoryId);
}
