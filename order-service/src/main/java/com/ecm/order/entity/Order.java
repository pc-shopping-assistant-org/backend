package com.ecm.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", insertable = false, updatable = false, nullable = false)
    private UUID id;

    /** ref -> Identity Service (customers.account_id), no cross-DB FK. */
    @Column(name = "customer_id")
    private UUID customerId;

    /** ref -> Promotion Service (discounts.id), no cross-DB FK. */
    @Column(name = "order_discount_id")
    private UUID orderDiscountId;

    @Column(name = "shipping_method_id", nullable = false)
    private UUID shippingMethodId;

    @Column(name = "subtotal_amount", nullable = false)
    private Long subtotalAmount;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    /** Snapshot of the shipping fee at order time — not recalculated if the method's fee changes later. */
    @Column(name = "shipping_fee", nullable = false)
    private Long shippingFee;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "order_time", nullable = false)
    private Instant orderTime;

    @Column(name = "note")
    private String note;

    /** Snapshot of the delivery address at order time. */
    @Column(name = "delivery_address", nullable = false, length = 500)
    private String deliveryAddress;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 15)
    private String recipientPhone;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** ref -> Identity Service (employees.account_id) — the staff member who created the order on the customer's behalf, if any. */
    @Column(name = "created_by")
    private UUID createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** ref -> Identity Service (employees.account_id), no cross-DB FK. */
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;
}
