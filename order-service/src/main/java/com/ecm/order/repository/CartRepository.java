package com.ecm.order.repository;

import com.ecm.order.entity.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByCustomerId(UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.customerId = :customerId")
    Optional<Cart> lockByCustomerId(@Param("customerId") UUID customerId);

    /** Creates the customer's cart unless it exists; two concurrent calls cannot create two carts nor fail. */
    @Modifying
    @Query(value = "INSERT INTO carts (customer_id) VALUES (:customerId) ON CONFLICT (customer_id) DO NOTHING", nativeQuery = true)
    void insertIfAbsent(@Param("customerId") UUID customerId);
}
