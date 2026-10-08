package com.ecm.payment.repository;

import com.ecm.payment.entity.Payment;
import com.ecm.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    /** The payments of one order that belong to the customer. */
    List<Payment> findByOrderIdAndCustomerIdOrderByCreatedAtAsc(UUID orderId, UUID customerId);

    boolean existsByProviderTransactionCodeAndIdNot(String providerTransactionCode, UUID id);

    List<Payment> findByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Every argument is never null, because an untyped null breaks "IS NULL" on PostgreSQL: a flag says whether a filter
     * is on, and a filter that is off carries a placeholder. An empty {@code code} matches every payment.
     */
    @Query("SELECT p FROM Payment p WHERE (:anyStatus = true OR p.status = :status) " +
            "AND (:anyOrder = true OR p.orderId = :orderId) " +
            "AND (:anyCustomer = true OR p.customerId IN :customerIds) " +
            "AND (:code = '' OR LOWER(p.providerTransactionCode) LIKE LOWER(CONCAT('%', :code, '%'))) " +
            "AND p.createdAt >= :createdFrom AND p.createdAt < :createdTo")
    Page<Payment> search(@Param("anyStatus") boolean anyStatus, @Param("status") PaymentStatus status,
                         @Param("anyOrder") boolean anyOrder, @Param("orderId") UUID orderId,
                         @Param("anyCustomer") boolean anyCustomer, @Param("customerIds") Collection<UUID> customerIds,
                         @Param("code") String code,
                         @Param("createdFrom") Instant createdFrom, @Param("createdTo") Instant createdTo,
                         Pageable pageable);
}
