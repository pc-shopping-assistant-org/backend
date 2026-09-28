package com.ecm.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code paidAt} is required once {@code status} is {@link PaymentStatus#PAID} — enforced
 * by a DB CHECK, not in Java. Only one PAID payment per {@code orderId} is allowed (partial
 * unique index in the migration); failed/pending attempts for the same order can coexist.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", insertable = false, updatable = false, nullable = false)
    private UUID id;

    /**
     * ref -> Order Service (orders.id), no cross-DB FK.
     */
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "payment_method_id", nullable = false)
    private UUID paymentMethodId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "provider_transaction_code", unique = true, length = 100)
    private String providerTransactionCode;

    /**
     * Identifies the specific caller attempt that created this row (the saga's triggering
     * event id) — lets a retried create-payment call return the existing row instead of
     * inserting a duplicate PENDING payment. Optional: null for payments created manually.
     */
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * ref -> Identity Service (employees.account_id) — set when created manually by staff.
     */
    @Column(name = "created_by")
    private UUID createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * ref -> Identity Service (employees.account_id), no cross-DB FK.
     */
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;
}
