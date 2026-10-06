package com.ecm.identity.repository;

import com.ecm.identity.dto.response.EmployeeResponse;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    String FROM_EMPLOYEE = "FROM Employee e JOIN Account a ON a.id = e.accountId ";

    String SELECT_RESPONSE = "SELECT new com.ecm.identity.dto.response.EmployeeResponse(a.id, a.email, a.phone, a.status, " +
            "e.firstName, e.lastName, e.gender, e.birthday, e.address, e.avatarFileId, e.createdAt) " + FROM_EMPLOYEE;

    String FILTER = "WHERE (:filterByStatus = false OR a.status = :status) AND (:keyword = '' " +
            "OR LOWER(CONCAT(e.firstName, ' ', e.lastName)) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(CONCAT(e.lastName, ' ', e.firstName)) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(a.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR a.phone LIKE CONCAT('%', :keyword, '%'))";

    @Query(value = SELECT_RESPONSE + FILTER + " ORDER BY e.createdAt DESC, e.accountId DESC",
            countQuery = "SELECT COUNT(e) " + FROM_EMPLOYEE + FILTER)
    Page<EmployeeResponse> search(@Param("filterByStatus") boolean filterByStatus,
                                  @Param("status") AccountStatus status, // never null: an untyped null breaks the comparison on PostgreSQL
                                  @Param("keyword") String keyword, // never null: an untyped null breaks LOWER() on PostgreSQL; "" matches everything
                                  Pageable pageable);

    @Query(SELECT_RESPONSE + "WHERE e.accountId = :id")
    Optional<EmployeeResponse> findDetail(@Param("id") UUID id);
}
