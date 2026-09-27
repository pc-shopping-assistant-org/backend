package com.ecm.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

/**
 * Named {@code Option}, not {@code Options}, to match Java class-naming convention — maps to the {@code options} table.
 */
@Entity
@Table(name = "options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Option {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", insertable = false, updatable = false, nullable = false)
    private UUID id;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "value", nullable = false)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
