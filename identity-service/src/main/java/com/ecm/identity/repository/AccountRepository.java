package com.ecm.identity.repository;

import com.ecm.identity.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);
}
