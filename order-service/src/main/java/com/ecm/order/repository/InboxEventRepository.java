package com.ecm.order.repository;

import com.ecm.order.entity.InboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InboxEventRepository extends JpaRepository<InboxEvent, UUID> {
}
