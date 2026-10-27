package com.ecm.catalog.repository;

import com.ecm.catalog.entity.HardwareTypeEntity;
import com.ecm.catalog.entity.CatalogStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HardwareTypeRepository extends JpaRepository<HardwareTypeEntity, UUID> {

    List<HardwareTypeEntity> findByStatusOrderByDisplayOrderAscCodeAsc(CatalogStatus status);

    Optional<HardwareTypeEntity> findByCodeAndStatus(String code, CatalogStatus status);

}
