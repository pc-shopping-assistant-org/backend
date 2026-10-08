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

    /** Snapshot of the product name at order time. */
    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    @Column(name = "sku", nullable = false, updatable = false, length = 100)
    private String sku;

    /** The options joined into one line, for example "Storage: 256GB, Color: Blue". */
    @Column(name = "variant_label", updatable = false, length = 500)
    private String variantLabel;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    /**
     * Snapshot of the variant price at order time, not recalculated if the catalog price changes later.
     */
    @Column(name = "unit_price", nullable = false, updatable = false)
    private Long unitPrice;

    /**
     * ref -> Promotion Service (discounts.id), no cross-DB FK.
     */
    @Column(name = "discount_id", updatable = false)
    private UUID discountId;

    /** The discount of the whole line, not per unit. */
    @Column(name = "discount_amount", nullable = false, updatable = false)
    private Long discountAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
