package com.ecm.catalog.repository;

import com.ecm.catalog.entity.AttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttributeDefinitionRepository extends JpaRepository<AttributeDefinition, UUID> {

    boolean existsByKey(String key);

    List<AttributeDefinition> findAllByOrderByDisplayNameAsc();
}
