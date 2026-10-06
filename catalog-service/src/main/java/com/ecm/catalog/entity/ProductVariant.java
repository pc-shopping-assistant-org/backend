package com.ecm.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", insertable = false, updatable = false, nullable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "model", length = 100)
    private String model;

    /**
     * ref -> Media Service (files.id), no cross-DB FK. Optional variant-specific image.
     */
    @Column(name = "image_file_id")
    private UUID imageFileId;

    @Column(name = "description")
    private String description;

    @Column(name = "warranty_months", nullable = false)
    private int warrantyMonths;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "release_at")
    private LocalDate releaseAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private CatalogStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * ref -> Identity Service (employees.account_id), no cross-DB FK.
     */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * ref -> Identity Service (employees.account_id), no cross-DB FK.
     */
    @Column(name = "updated_by")
    private UUID updatedBy;
}
