package com.ecm.catalog.repository;

import com.ecm.catalog.entity.InboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InboxEventRepository extends JpaRepository<InboxEvent, UUID> {
}
