package com.ecm.order.repository;

import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    boolean existsByInvoiceNumber(String invoiceNumber);

    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Keyset page, newest first on (createdAt, id), of the orders of one customer. The cursor is the (createdAt, id)
     * of the last order of the previous page, or a point after every order for the first page. The status and the
     * keyword are optional; a keyword matches an order by its id or by its invoice number.
     */
    @Query("""
            SELECT o FROM Order o
            WHERE o.customerId = :customerId
              AND (:anyStatus = TRUE OR o.status = :status)
              AND (:anyKeyword = TRUE OR o.id = :orderId OR o.invoiceNumber = :invoiceNumber)
              AND (o.createdAt < :cursorCreatedAt OR (o.createdAt = :cursorCreatedAt AND o.id < :cursorId))
            ORDER BY o.createdAt DESC, o.id DESC
            """)
    List<Order> findCustomerPage(
            @Param("customerId") UUID customerId,
            @Param("anyStatus") boolean anyStatus,
            @Param("status") OrderStatus status,
            @Param("anyKeyword") boolean anyKeyword,
            @Param("orderId") UUID orderId,
            @Param("invoiceNumber") String invoiceNumber,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    @Query("SELECT o FROM Order o WHERE (:status IS NULL OR o.status = :status) " +
            "AND (:customerId IS NULL OR o.customerId = :customerId) " +
            "AND o.createdAt >= :createdFrom " +
            "AND o.createdAt < :createdTo " +
            "AND (LOWER(CAST(o.id AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(o.invoiceNumber) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Order> searchAdminOrders(@Param("status") OrderStatus status,
                                  @Param("customerId") UUID customerId,
                                  @Param("createdFrom") Instant createdFrom, // never null: an untyped null breaks "IS NULL" on PostgreSQL
                                  @Param("createdTo") Instant createdTo,
                                  @Param("keyword") String keyword, // never null: an untyped null breaks LOWER() on PostgreSQL; "" matches everything
                                  Pageable pageable);
}
