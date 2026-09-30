package com.ecm.catalog.repository;

import com.ecm.catalog.entity.CatalogStatus;
import com.ecm.catalog.entity.Option;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OptionRepository extends JpaRepository<Option, UUID> {

    @Query("SELECT o FROM Option o WHERE o.id IN :ids AND o.status = :status")
    List<Option> findByIdInAndStatus(@Param("ids") List<UUID> ids, @Param("status") CatalogStatus status);
}
