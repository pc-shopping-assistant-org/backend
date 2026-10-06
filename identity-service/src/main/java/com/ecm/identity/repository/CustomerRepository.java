package com.ecm.identity.repository;

import com.ecm.identity.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /** The accounts of the customers whose full name, in either order, contains the text. */
    @Query("SELECT c.accountId FROM Customer c WHERE LOWER(CONCAT(c.firstName, ' ', c.lastName)) LIKE LOWER(CONCAT('%', :name, '%')) " +
            "OR LOWER(CONCAT(c.lastName, ' ', c.firstName)) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<UUID> findAccountIdsByName(@Param("name") String name, Pageable pageable);
}
