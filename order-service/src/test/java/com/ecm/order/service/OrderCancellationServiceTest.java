package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderItem;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.mapper.OrderMapper;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderCancellationServiceTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private OrderStatusService statusService;
    private OrderOutbox outbox;
    private OrderCancellationService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        statusService = mock(OrderStatusService.class);
        outbox = mock(OrderOutbox.class);
        service = new OrderCancellationService(orderRepository, orderItemRepository, statusService, outbox, Mappers.getMapper(OrderMapper.class));
        when(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(OrderItem.builder().orderId(ORDER_ID).productName("RAM").quantity(1).unitPrice(10L).discountAmount(0L).build()));
    }

    private Order order(OrderStatus status) {
        Order order = Order.builder().id(ORDER_ID).customerId(CUSTOMER).invoiceNumber("INV-AAAAAAAAAA").status(status).totalAmount(10L).build();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(statusService.transition(eq(order), eq(OrderStatus.CANCELLED), any(), any())).thenAnswer(call -> {
            order.setStatus(OrderStatus.CANCELLED);
            return order;
        });
        return order;
    }

    @Test
    void anOrderWaitingForPaymentOrConfirmationCanBeCancelled() {
        for (OrderStatus status : List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_CONFIRMATION)) {
            Order order = order(status);

            assertEquals(OrderStatus.CANCELLED, service.cancelByCustomer(ORDER_ID, CUSTOMER, "  changed my mind ").status());

            verify(statusService).transition(order, OrderStatus.CANCELLED, null, "changed my mind");
            clearInvocations(statusService);
        }
    }

    @Test
    void cancellingGivesTheStockBackAndAnnouncesTheCancellation() {
        Order order = order(OrderStatus.PENDING_PAYMENT);

        service.cancelByCustomer(ORDER_ID, CUSTOMER, null);

        verify(outbox).releaseStock(eq(order), any());
        verify(outbox).orderCancelled(order, null);
    }

    @Test
    void aBlankReasonIsStoredAsNothing() {
        Order order = order(OrderStatus.PENDING_PAYMENT);

        service.cancelByCustomer(ORDER_ID, CUSTOMER, "   ");

        verify(statusService).transition(order, OrderStatus.CANCELLED, null, null);
    }

    @Test
    void aConfirmedOrCancelledOrderCannotBeCancelled() {
        for (OrderStatus status : List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPING, OrderStatus.COMPLETED, OrderStatus.CANCELLED)) {
            order(status);

            assertThrows(BusinessException.class, () -> service.cancelByCustomer(ORDER_ID, CUSTOMER, null));
        }
        verifyNoInteractions(outbox);
        verify(statusService, never()).transition(any(), any(), any(), any());
    }

    @Test
    void anotherCustomerOrderLooksMissing() {
        order(OrderStatus.PENDING_PAYMENT);

        assertThrows(ResourceNotFoundException.class, () -> service.cancelByCustomer(ORDER_ID, UUID.randomUUID(), null));
        verifyNoInteractions(outbox);
    }

    @Test
    void anUnknownOrderIsNotFound() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.cancelByCustomer(ORDER_ID, CUSTOMER, null));
    }
}
