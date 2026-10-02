package com.ecm.order.repository;

import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Order> findByCustomerIdAndStatus(UUID customerId, OrderStatus status, Pageable pageable);

    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<Order> findByIdAndCustomerIdAndStatus(UUID id, UUID customerId, OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT o FROM Order o WHERE o.customerId = :customerId " +
           "AND (CAST(o.id AS string) LIKE %:keyword% OR o.invoiceNumber LIKE %:keyword%)")
    Page<Order> searchByCustomerAndKeyword(@Param("customerId") UUID customerId,
                                           @Param("keyword") String keyword,
                                           Pageable pageable);
}
