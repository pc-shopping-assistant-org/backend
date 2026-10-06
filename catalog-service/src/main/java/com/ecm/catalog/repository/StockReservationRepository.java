package com.ecm.catalog.repository;

import com.ecm.catalog.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    /** Returns 1 when the order held a reservation, which is now gone, and 0 when it held none. */
    @Modifying
    @Query("DELETE FROM StockReservation r WHERE r.orderId = :orderId")
    int deleteByOrderIdReturningCount(@Param("orderId") UUID orderId);
}
