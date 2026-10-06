package com.ecm.identity.repository;

import com.ecm.identity.dto.response.CustomerResponse;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    String FROM_CUSTOMER = "FROM Customer c JOIN Account a ON a.id = c.accountId ";

    String SELECT_RESPONSE = "SELECT new com.ecm.identity.dto.response.CustomerResponse(a.id, a.email, a.phone, a.status, " +
            "c.firstName, c.lastName, c.gender, c.birthday, c.avatarFileId, c.createdAt) " + FROM_CUSTOMER;

    String FILTER = "WHERE (:filterByStatus = false OR a.status = :status) " +
            "AND c.createdAt >= :createdFrom AND c.createdAt < :createdTo AND (:keyword = '' " +
            "OR LOWER(CONCAT(c.firstName, ' ', c.lastName)) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(CONCAT(c.lastName, ' ', c.firstName)) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(a.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR a.phone LIKE CONCAT('%', :keyword, '%'))";

    /** The accounts of the customers whose full name, in either order, contains the text. */
    @Query("SELECT c.accountId FROM Customer c WHERE LOWER(CONCAT(c.firstName, ' ', c.lastName)) LIKE LOWER(CONCAT('%', :name, '%')) " +
            "OR LOWER(CONCAT(c.lastName, ' ', c.firstName)) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<UUID> findAccountIdsByName(@Param("name") String name, Pageable pageable);

    @Query(value = SELECT_RESPONSE + FILTER + " ORDER BY c.createdAt DESC, c.accountId DESC",
            countQuery = "SELECT COUNT(c) " + FROM_CUSTOMER + FILTER)
    Page<CustomerResponse> search(@Param("filterByStatus") boolean filterByStatus,
                                  @Param("status") AccountStatus status, // never null: an untyped null breaks the comparison on PostgreSQL
                                  @Param("createdFrom") Instant createdFrom, // never null: use Instant.EPOCH for "no lower bound"
                                  @Param("createdTo") Instant createdTo, // never null: use a far-future instant for "no upper bound"
                                  @Param("keyword") String keyword, // never null: an untyped null breaks LOWER() on PostgreSQL; "" matches everything
                                  Pageable pageable);

    @Query(SELECT_RESPONSE + "WHERE c.accountId = :id")
    Optional<CustomerResponse> findDetail(@Param("id") UUID id);
}
