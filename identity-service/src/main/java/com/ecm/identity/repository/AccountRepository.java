package com.ecm.identity.repository;

import com.ecm.identity.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    @Query("SELECT a FROM Account a WHERE LOWER(a.email) = LOWER(:identifier) OR a.phone = :identifier")
    Optional<Account> findByLoginIdentifier(@Param("identifier") String identifier);

    Optional<Account> findByEmailIgnoreCase(String email);

    Optional<Account> findByGoogleSubject(String googleSubject);
}
