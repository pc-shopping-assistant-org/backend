package com.ecm.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImage {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", insertable = false, updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name")
    private String name;

    @Column(name = "product_variant_id", nullable = false)
    private UUID productVariantId;

    /**
     * ref -> Media Service (files.id), no cross-DB FK.
     */
    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "is_main", nullable = false)
    private boolean isMain;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private CatalogStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
