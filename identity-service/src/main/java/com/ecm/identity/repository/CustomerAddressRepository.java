package com.ecm.identity.repository;

import com.ecm.identity.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    List<CustomerAddress> findByCustomerIdOrderByIsDefaultDesc(UUID customerId);

    Optional<CustomerAddress> findByIdAndCustomerId(UUID id, UUID customerId);

    @Modifying
    @Query("UPDATE CustomerAddress a SET a.isDefault = false WHERE a.customerId = :customerId AND a.isDefault = true")
    void clearDefault(@Param("customerId") UUID customerId);

    long countByCustomerId(UUID customerId);

    boolean existsByIdAndCustomerId(UUID id, UUID customerId);
}
