package com.ecm.catalog.repository;

import com.ecm.catalog.entity.OutboxEvent;
import com.ecm.catalog.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    boolean existsByAggregateIdAndEventType(UUID aggregateId, String eventType);
}
