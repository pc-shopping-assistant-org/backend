package com.ecm.identity.repository;

import com.ecm.identity.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {
}
