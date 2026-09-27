package com.ecm.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", insertable = false, updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /**
     * ref -> Catalog Service (product_variants.id), no cross-DB FK.
     */
    @Column(name = "product_variant_id", nullable = false)
    private UUID productVariantId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /**
     * Snapshot of the variant's price at order time — not recalculated if the catalog price changes later.
     */
    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    /**
     * ref -> Promotion Service (discounts.id), no cross-DB FK.
     */
    @Column(name = "item_discount_id")
    private UUID itemDiscountId;

    @Column(name = "item_discount", nullable = false)
    private Long itemDiscount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderItemStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
