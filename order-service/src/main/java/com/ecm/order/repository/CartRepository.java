package com.ecm.order.repository;

import com.ecm.order.entity.Cart;
import com.ecm.order.entity.CartStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    @Query("select c from Cart c where c.customerId = :customerId and c.status = :status")
    Optional<Cart> findActiveByCustomerId(@Param("customerId") UUID customerId, @Param("status") CartStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.customerId = :customerId and c.status = :status")
    Optional<Cart> lockActiveByCustomerId(@Param("customerId") UUID customerId, @Param("status") CartStatus status);

    @Query("select c from Cart c where c.sessionToken = :sessionToken and c.status = :status")
    Optional<Cart> findActiveBySessionToken(@Param("sessionToken") String sessionToken, @Param("status") CartStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.sessionToken = :sessionToken and c.status = :status")
    Optional<Cart> lockActiveBySessionToken(@Param("sessionToken") String sessionToken, @Param("status") CartStatus status);
}
