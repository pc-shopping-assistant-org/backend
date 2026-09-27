package com.ecm.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReview {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", insertable = false, updatable = false, nullable = false)
    private UUID id;

    /**
     * ref -> Order Service (order_items.id), no cross-DB FK. Unique: one review per order line.
     */
    @Column(name = "order_item_id", nullable = false, unique = true)
    private UUID orderItemId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private CatalogStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
