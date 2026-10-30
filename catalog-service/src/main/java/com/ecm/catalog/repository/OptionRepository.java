package com.ecm.catalog.repository;

import com.ecm.catalog.entity.Option;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OptionRepository extends JpaRepository<Option, UUID> {

    /**
     * Inserts the option unless one with the same name and value, ignoring case, exists. A concurrent insert of the same
     * option waits for the other transaction and then does nothing, instead of failing on the unique index.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO options (name, value) VALUES (:name, :value) ON CONFLICT ((lower(name)), (lower(value))) DO NOTHING",
            nativeQuery = true)
    void insertIfAbsent(@Param("name") String name, @Param("value") String value);

    @Query("SELECT o FROM Option o WHERE lower(o.name) = lower(:name) AND lower(o.value) = lower(:value)")
    Optional<Option> findByNameAndValueIgnoringCase(@Param("name") String name, @Param("value") String value);
}
