package com.ecm.order.messaging;

import com.ecm.order.client.PaymentServiceClient;
import com.ecm.order.dto.request.CreatePaymentRequest;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.messaging.event.PaymentCompletedEvent;
import com.ecm.order.messaging.event.PaymentFailedEvent;
import com.ecm.order.messaging.event.StockReserveFailedEvent;
import com.ecm.order.messaging.event.StockReservedEvent;
import com.ecm.order.messaging.kafka.consumer.PaymentEventConsumer;
import com.ecm.order.messaging.kafka.consumer.StockEventConsumer;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import com.ecm.order.service.OrderOutbox;
import com.ecm.order.service.OrderStatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SagaConsumersTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID PAYMENT_METHOD = UUID.randomUUID();

    private final ObjectMapper mapper = new ObjectMapper();
    private InboxGuard inbox;
    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private OrderStatusService statusService;
    private OrderOutbox outbox;
    private PaymentServiceClient paymentClient;
    private StockEventConsumer stockConsumer;
    private PaymentEventConsumer paymentConsumer;

    @BeforeEach
    void setUp() {
        inbox = mock(InboxGuard.class);
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        statusService = mock(OrderStatusService.class);
        outbox = mock(OrderOutbox.class);
        paymentClient = mock(PaymentServiceClient.class);
        stockConsumer = new StockEventConsumer(inbox, orderRepository, statusService, paymentClient, mapper);
        paymentConsumer = new PaymentEventConsumer(inbox, orderRepository, orderItemRepository, statusService, outbox, mapper);
    }

    private Order order(OrderStatus status) {
        Order order = Order.builder().id(ORDER_ID).status(status).paymentMethodId(PAYMENT_METHOD).totalAmount(777L).build();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(statusService.transition(any(), any(), any(), any())).thenReturn(order);
        when(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        return order;
    }

    private String json(Object event) throws Exception {
        return mapper.writeValueAsString(event);
    }

    // ---- stock reserved / failed ----

    @Test
    void reservedStockStartsThePaymentWithTheOrderAsItsKey() throws Exception {
        order(OrderStatus.PENDING_PAYMENT);

        stockConsumer.onStockReserved(json(new StockReservedEvent(EVENT_ID, ORDER_ID)));

        verify(paymentClient).create(new CreatePaymentRequest(ORDER_ID, PAYMENT_METHOD, 777L, ORDER_ID.toString()));
    }

    @Test
    void reservedStockOfACancelledOrderStartsNoPayment() throws Exception {
        order(OrderStatus.CANCELLED);

        stockConsumer.onStockReserved(json(new StockReservedEvent(EVENT_ID, ORDER_ID)));

        verifyNoInteractions(paymentClient);
    }

    @Test
    void aRedeliveredEventIsIgnored() throws Exception {
        order(OrderStatus.PENDING_PAYMENT);
        when(inbox.alreadyProcessed(eq(EVENT_ID), anyString())).thenReturn(true);

        stockConsumer.onStockReserved(json(new StockReservedEvent(EVENT_ID, ORDER_ID)));
        stockConsumer.onStockReserveFailed(json(new StockReserveFailedEvent(EVENT_ID, ORDER_ID, "x")));

        verifyNoInteractions(paymentClient, statusService);
    }

    @Test
    void failedReservationCancelsAWaitingOrderWithTheReason() throws Exception {
        Order order = order(OrderStatus.PENDING_PAYMENT);

        stockConsumer.onStockReserveFailed(json(new StockReserveFailedEvent(EVENT_ID, ORDER_ID, "Insufficient stock")));

        verify(statusService).transition(order, OrderStatus.CANCELLED, null, "Insufficient stock");
    }

    @Test
    void failedReservationLeavesAnAlreadyCancelledOrderAlone() throws Exception {
        order(OrderStatus.CANCELLED);

        stockConsumer.onStockReserveFailed(json(new StockReserveFailedEvent(EVENT_ID, ORDER_ID, "Insufficient stock")));

        verify(statusService, never()).transition(any(), any(), any(), any());
    }

    // ---- payment completed / failed ----

    @Test
    void aPaidOrderWaitsForConfirmation() throws Exception {
        Order order = order(OrderStatus.PENDING_PAYMENT);

        paymentConsumer.onPaymentCompleted(json(new PaymentCompletedEvent(EVENT_ID, ORDER_ID, UUID.randomUUID())));

        verify(statusService).transition(order, OrderStatus.PENDING_CONFIRMATION, null, null);
    }

    @Test
    void aPaymentForACancelledOrderChangesNothing() throws Exception {
        order(OrderStatus.CANCELLED);

        paymentConsumer.onPaymentCompleted(json(new PaymentCompletedEvent(EVENT_ID, ORDER_ID, UUID.randomUUID())));

        verify(statusService, never()).transition(any(), any(), any(), any());
    }

    @Test
    void aFailedPaymentCancelsTheOrderAndReleasesItsStock() throws Exception {
        Order order = order(OrderStatus.PENDING_PAYMENT);

        paymentConsumer.onPaymentFailed(json(new PaymentFailedEvent(EVENT_ID, ORDER_ID, UUID.randomUUID(), "Card declined")));

        verify(statusService).transition(order, OrderStatus.CANCELLED, null, "Card declined");
        verify(outbox).releaseStock(eq(order), any());
    }

    @Test
    void aFailedPaymentOfAnOrderNoLongerWaitingChangesNothing() throws Exception {
        order(OrderStatus.PENDING_CONFIRMATION);

        paymentConsumer.onPaymentFailed(json(new PaymentFailedEvent(EVENT_ID, ORDER_ID, UUID.randomUUID(), "late")));

        verifyNoInteractions(outbox);
        verify(statusService, never()).transition(any(), any(), any(), any());
    }
}
