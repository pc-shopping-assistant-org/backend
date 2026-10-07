package com.ecm.order.messaging.kafka.consumer;

import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.messaging.InboxGuard;
import com.ecm.order.messaging.event.PaymentCompletedEvent;
import com.ecm.order.messaging.event.PaymentFailedEvent;
import com.ecm.order.messaging.kafka.KafkaTopics;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import com.ecm.order.service.OrderOutbox;
import com.ecm.order.service.OrderStatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reacts to the outcome of a payment, the last step of the checkout saga. Only an order that still waits for its
 * payment is moved; a payment result for an order that was cancelled meanwhile changes nothing.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private static final String CONSUMER_NAME = "order-service.payment-event-consumer";

    private final InboxGuard inboxGuard;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusService orderStatusService;
    private final OrderOutbox orderOutbox;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED)
    @Transactional
    @SneakyThrows
    public void onPaymentCompleted(String payload) {
        PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
        if (inboxGuard.alreadyProcessed(event.eventId(), CONSUMER_NAME)) {
            return;
        }
        Order order = orderRepository.findByIdForUpdate(event.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", event.orderId()));

        // 1. Paid: the order now waits for the shop to confirm it
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            orderStatusService.transition(order, OrderStatus.PENDING_CONFIRMATION, null, null);
        }
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED)
    @Transactional
    @SneakyThrows
    public void onPaymentFailed(String payload) {
        PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
        if (inboxGuard.alreadyProcessed(event.eventId(), CONSUMER_NAME)) {
            return;
        }
        Order order = orderRepository.findByIdForUpdate(event.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", event.orderId()));

        // 1. Payment failed: cancel the order and give its stock back
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            Order cancelled = orderStatusService.transition(order, OrderStatus.CANCELLED, null, event.reason());
            orderOutbox.releaseStock(cancelled, orderItemRepository.findByOrderId(cancelled.getId()));
        }
    }
}
