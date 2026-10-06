package com.ecm.catalog.repository;

import com.ecm.catalog.entity.Option;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OptionRepository extends JpaRepository<Option, UUID> {

    Optional<Option> findByNameIgnoreCaseAndValueIgnoreCase(String name, String value);
}
