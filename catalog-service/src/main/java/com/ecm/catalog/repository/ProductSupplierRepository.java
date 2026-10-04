package com.ecm.catalog.repository;

import com.ecm.catalog.entity.ProductSupplier;
import com.ecm.catalog.entity.ProductSupplierId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProductSupplierRepository extends JpaRepository<ProductSupplier, ProductSupplierId> {
    List<ProductSupplier> findByProductId(UUID productId);
    void deleteByProductId(UUID productId);
    boolean existsBySupplierId(UUID supplierId);
}
