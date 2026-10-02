package com.ecm.order.repository;

import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Order> findByCustomerIdAndStatus(UUID customerId, OrderStatus status, Pageable pageable);

    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<Order> findByIdAndCustomerIdAndStatus(UUID id, UUID customerId, OrderStatus status);
}
