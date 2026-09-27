package com.ecm.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category_attribute_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryAttributeGroup {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", insertable = false, updatable = false, nullable = false)
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
