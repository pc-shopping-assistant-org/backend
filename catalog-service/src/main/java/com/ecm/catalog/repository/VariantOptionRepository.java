package com.ecm.catalog.repository;

import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.VariantOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VariantOptionRepository extends JpaRepository<VariantOption, UUID> {

    List<VariantOption> findByProductVariantIdAndStatus(@Param("variantId") UUID variantId,
                                                         @Param("status") CatalogStatus status);

    @Query("SELECT vo FROM VariantOption vo WHERE vo.productVariantId IN :variantIds AND vo.status = :status")
    List<VariantOption> findByProductVariantIdInAndStatus(@Param("variantIds") List<UUID> variantIds,
                                                           @Param("status") CatalogStatus status);
}
