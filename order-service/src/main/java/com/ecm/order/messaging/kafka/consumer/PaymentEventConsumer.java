package com.ecm.order.messaging.kafka.consumer;

import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderItem;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.entity.OutboxChannel;
import com.ecm.order.entity.OutboxEvent;
import com.ecm.order.entity.OutboxStatus;
import com.ecm.order.messaging.InboxGuard;
import com.ecm.order.messaging.event.PaymentCompletedEvent;
import com.ecm.order.messaging.event.PaymentFailedEvent;
import com.ecm.order.messaging.kafka.KafkaTopics;
import com.ecm.order.messaging.rabbitmq.RabbitTopology;
import com.ecm.order.messaging.rabbitmq.command.ReleaseStockCommand;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import com.ecm.order.repository.OutboxEventRepository;
import com.ecm.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Reacts to payment-service's outcome for a payment — the final saga step: confirm or roll back the order. */
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private static final String CONSUMER_NAME = "order-service.payment-event-consumer";
    private static final String AGGREGATE_TYPE_ORDER = "ORDER";

    private final InboxGuard inboxGuard;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED)
    @Transactional
    @SneakyThrows
    public void onPaymentCompleted(String payload) {
        PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
        if (inboxGuard.alreadyProcessed(event.eventId(), CONSUMER_NAME)) {
            return;
        }
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", event.orderId()));

        // 1. Payment succeeded — the order is confirmed.
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED)
    @Transactional
    @SneakyThrows
    public void onPaymentFailed(String payload) {
        PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
        if (inboxGuard.alreadyProcessed(event.eventId(), CONSUMER_NAME)) {
            return;
        }
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", event.orderId()));

        // 1. Payment failed — cancel the order.
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // 2. Release the stock reserved earlier for every item of this order.
        for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
            ReleaseStockCommand command = new ReleaseStockCommand(
                    UUID.randomUUID(), order.getId(), item.getProductVariantId(), item.getQuantity());
            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType(AGGREGATE_TYPE_ORDER)
                    .aggregateId(order.getId())
                    .eventType("ReleaseStockCommand")
                    .channel(OutboxChannel.RABBITMQ)
                    .destination(RabbitTopology.ROUTING_KEY_RELEASE)
                    .payload(objectMapper.writeValueAsString(command))
                    .status(OutboxStatus.PENDING)
                    .build());
        }
    }
}
