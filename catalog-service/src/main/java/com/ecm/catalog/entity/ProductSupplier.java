package com.ecm.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_suppliers")
@IdClass(ProductSupplierId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSupplier {

    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Id
    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
