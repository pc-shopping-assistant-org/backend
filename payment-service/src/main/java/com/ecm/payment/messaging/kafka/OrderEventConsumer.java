package com.ecm.payment.messaging.kafka;

import com.ecm.payment.messaging.InboxGuard;
import com.ecm.payment.messaging.event.OrderCancelledEvent;
import com.ecm.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reacts to what happens to an order: a cancelled order has nothing left to pay. */
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private static final String CONSUMER_NAME = "payment-service.order-event-consumer";

    private final InboxGuard inboxGuard;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED)
    @Transactional
    @SneakyThrows
    public void onOrderCancelled(String payload) {
        OrderCancelledEvent event = objectMapper.readValue(payload, OrderCancelledEvent.class);
        if (inboxGuard.alreadyProcessed(event.eventId(), CONSUMER_NAME)) {
            return;
        }
        paymentService.cancelPendingPayments(event.orderId());
    }
}
