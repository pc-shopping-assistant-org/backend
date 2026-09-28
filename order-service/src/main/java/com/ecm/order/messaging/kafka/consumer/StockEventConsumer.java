package com.ecm.order.messaging.kafka.consumer;

import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.order.client.PaymentServiceClient;
import com.ecm.order.dto.request.CreatePaymentRequest;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.messaging.InboxGuard;
import com.ecm.order.messaging.event.StockReserveFailedEvent;
import com.ecm.order.messaging.event.StockReservedEvent;
import com.ecm.order.messaging.kafka.KafkaTopics;
import com.ecm.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reacts to catalog-service's stock reservation result — the next step of the checkout saga.
 */
@Component
@RequiredArgsConstructor
public class StockEventConsumer {

    private static final String CONSUMER_NAME = "order-service.stock-event-consumer";

    private final InboxGuard inboxGuard;
    private final OrderRepository orderRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.STOCK_RESERVED)
    @Transactional
    @SneakyThrows
    public void onStockReserved(String payload) {
        StockReservedEvent event = objectMapper.readValue(payload, StockReservedEvent.class);
        if (inboxGuard.alreadyProcessed(event.eventId(), CONSUMER_NAME)) {
            return;
        }
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", event.orderId()));

        // 1. Stock is reserved — start the payment attempt for this order. Keyed by this
        // event's id so a Feign retry/event redelivery can't create a second PENDING payment.
        paymentServiceClient.create(new CreatePaymentRequest(
                order.getId(), order.getPaymentMethodId(), order.getTotalAmount(), event.eventId().toString()));
    }

    @KafkaListener(topics = KafkaTopics.STOCK_RESERVE_FAILED)
    @Transactional
    @SneakyThrows
    public void onStockReserveFailed(String payload) {
        StockReserveFailedEvent event = objectMapper.readValue(payload, StockReserveFailedEvent.class);
        if (inboxGuard.alreadyProcessed(event.eventId(), CONSUMER_NAME)) {
            return;
        }
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", event.orderId()));

        // 1. Out of stock — the order cannot proceed, cancel it immediately.
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }
}
