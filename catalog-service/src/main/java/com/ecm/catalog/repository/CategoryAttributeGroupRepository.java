package com.ecm.catalog.repository;

import com.ecm.catalog.entity.CategoryAttributeGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryAttributeGroupRepository extends JpaRepository<CategoryAttributeGroup, UUID> {

    List<CategoryAttributeGroup> findByCategoryIdOrderByDisplayOrderAscNameAsc(UUID categoryId);

    @Modifying
    @Query("DELETE FROM CategoryAttributeGroup g WHERE g.categoryId = :categoryId")
    void deleteByCategoryId(@Param("categoryId") UUID categoryId);
}
