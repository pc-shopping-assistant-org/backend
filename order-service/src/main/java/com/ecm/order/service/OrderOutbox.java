package com.ecm.order.service;

import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderItem;
import com.ecm.order.entity.OutboxChannel;
import com.ecm.order.entity.OutboxEvent;
import com.ecm.order.entity.OutboxStatus;
import com.ecm.order.messaging.event.OrderCancelledEvent;
import com.ecm.order.messaging.kafka.KafkaTopics;
import com.ecm.order.messaging.rabbitmq.RabbitTopology;
import com.ecm.order.messaging.rabbitmq.command.ReleaseStockCommand;
import com.ecm.order.messaging.rabbitmq.command.ReserveStockCommand;
import com.ecm.order.messaging.rabbitmq.command.StockItem;
import com.ecm.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Queues the messages of the order saga in the outbox, so they commit together with the business change that needs them. */
@Component
@RequiredArgsConstructor
public class OrderOutbox {

    private static final String AGGREGATE_TYPE_ORDER = "ORDER";
    private static final String RESERVE_STOCK_COMMAND = "ReserveStockCommand";
    private static final String RELEASE_STOCK_COMMAND = "ReleaseStockCommand";
    private static final String ORDER_CANCELLED_EVENT = "OrderCancelledEvent";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void reserveStock(Order order, List<OrderItem> items) {
        enqueue(order, RESERVE_STOCK_COMMAND, OutboxChannel.RABBITMQ, RabbitTopology.ROUTING_KEY_RESERVE,
                new ReserveStockCommand(UUID.randomUUID(), order.getId(), stockItems(items)));
    }

    /** Harmless for an order whose stock was never reserved: the Catalog Service gives back only what the order holds. */
    public void releaseStock(Order order, List<OrderItem> items) {
        enqueue(order, RELEASE_STOCK_COMMAND, OutboxChannel.RABBITMQ, RabbitTopology.ROUTING_KEY_RELEASE,
                new ReleaseStockCommand(UUID.randomUUID(), order.getId(), stockItems(items)));
    }

    public void orderCancelled(Order order, String reason) {
        enqueue(order, ORDER_CANCELLED_EVENT, OutboxChannel.KAFKA, KafkaTopics.ORDER_CANCELLED,
                new OrderCancelledEvent(UUID.randomUUID(), order.getId(), reason));
    }

    private static List<StockItem> stockItems(List<OrderItem> items) {
        return items.stream().map(item -> new StockItem(item.getProductVariantId(), item.getQuantity())).toList();
    }

    private void enqueue(Order order, String eventType, OutboxChannel channel, String destination, Object payload) {
        try {
            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType(AGGREGATE_TYPE_ORDER)
                    .aggregateId(order.getId())
                    .eventType(eventType)
                    .channel(channel)
                    .destination(destination)
                    .payload(objectMapper.writeValueAsString(payload))
                    .status(OutboxStatus.PENDING)
                    .build());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize " + eventType + " of order " + order.getId(), ex);
        }
    }
}
