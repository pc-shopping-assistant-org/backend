package com.ecm.promotion.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "discounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Discount {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", insertable = false, updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", unique = true, length = 50)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    private DiscountType discountType;

    /**
     * Percent (1-100) or a fixed amount (> 0) depending on {@link #discountType}; enforced by a DB CHECK.
     */
    @Column(name = "value", nullable = false)
    private int value;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_scope", nullable = false, length = 20)
    private ApplicationScope applicationScope;

    @Column(name = "min_order_amount", nullable = false)
    private Long minOrderAmount;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "description")
    private String description;

    /**
     * ref -> Identity Service (employees.account_id), no cross-DB FK.
     */
    @Column(name = "created_by")
    private UUID createdBy;

    /**
     * ref -> Identity Service (employees.account_id), no cross-DB FK.
     */
    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DiscountStatus status;
}
