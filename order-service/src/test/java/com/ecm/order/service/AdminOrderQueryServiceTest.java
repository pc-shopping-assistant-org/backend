package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.ApiResponse;
import com.ecm.order.client.PaymentServiceClient;
import com.ecm.order.dto.response.CursorPageResponse;
import com.ecm.order.dto.response.OrderDetailResponse;
import com.ecm.order.dto.response.OrderSummaryResponse;
import com.ecm.order.dto.response.PaymentResponse;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderItem;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.helper.PaymentLookup;
import com.ecm.order.mapper.OrderMapper;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import com.ecm.order.repository.OrderStatusHistoryRepository;
import com.ecm.order.entity.OrderStatusHistory;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import com.ecm.order.dto.request.AdminOrderSearchRequest;
import com.ecm.order.dto.response.AdminOrderDetailResponse;
import com.ecm.order.dto.response.AdminOrderSummaryResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.ecm.order.dto.response.InvoiceDetailResponse;
import com.ecm.order.dto.response.InvoiceSummaryResponse;
import com.ecm.order.dto.request.AdminInvoiceSearchRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminOrderQueryServiceTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID ORDER_ID = UUID.fromString("0192f3a4-1b2c-7d3e-8f40-516273849abc");
    private static final String TOKEN = "Bearer token";

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private PaymentServiceClient paymentClient;
    private OrderStatusHistoryRepository historyRepository;
    private AdminOrderQueryService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        paymentClient = mock(PaymentServiceClient.class);
        historyRepository = mock(OrderStatusHistoryRepository.class);
        service = new AdminOrderQueryService(orderRepository, orderItemRepository, historyRepository, new PaymentLookup(paymentClient),
                Mappers.getMapper(OrderMapper.class));
        when(orderItemRepository.findByOrderIdIn(anyCollection())).thenReturn(List.of());
    }

    private static Order order(UUID id, Instant createdAt) {
        return Order.builder().id(id).customerId(CUSTOMER).invoiceNumber("INV-ABCDEFGH23").status(OrderStatus.PENDING_PAYMENT)
                .totalAmount(100L).createdAt(createdAt).build();
    }

    // ---- UC-ADM-ORD-001 list / UC-ADM-ORD-002 detail, for the shop ----

    private static AdminOrderSearchRequest filter(String keyword, OrderStatus status, Instant from, Instant to) {
        return new AdminOrderSearchRequest(keyword, status, from, to, null);
    }

    @Test
    void theShopListShowsTheRecipientAndTheLinesOfEachOrder() {
        Order order = order(ORDER_ID, Instant.now());
        order.setRecipientName("Nguyen Van A");
        order.setRecipientPhone("0912345678");
        when(orderRepository.searchAdminOrders(any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(order)));
        when(orderItemRepository.findByOrderIdIn(anyCollection())).thenReturn(List.of(
                OrderItem.builder().orderId(ORDER_ID).productName("RAM").build(), OrderItem.builder().orderId(ORDER_ID).productName("SSD").build()));

        AdminOrderSummaryResponse row = service.searchOrders(filter(null, null, null, null), 0, 20).getContent().getFirst();

        assertEquals("Nguyen Van A", row.recipientName());
        assertEquals("0912345678", row.recipientPhone());
        assertEquals(CUSTOMER, row.customerId());
        assertEquals(2, row.itemCount());
        assertEquals("RAM", row.firstProductName());
    }

    @Test
    void theShopFilterIsPassedOnWithOpenEndedDatesAndATrimmedKeyword() {
        when(orderRepository.searchAdminOrders(any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.searchOrders(filter("  an  ", OrderStatus.SHIPPING, null, null), 2, 30);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).searchAdminOrders(eq(OrderStatus.SHIPPING), any(), eq(Instant.EPOCH), any(), eq("an"), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(30, pageable.getValue().getPageSize());
    }

    @Test
    void aBadPageOrPeriodIsRejected() {
        Instant now = Instant.now();

        assertThrows(BusinessException.class, () -> service.searchOrders(filter(null, null, null, null), -1, 20));
        assertThrows(BusinessException.class, () -> service.searchOrders(filter(null, null, null, null), 0, 101));
        assertThrows(BusinessException.class, () -> service.searchOrders(filter(null, null, now, now), 0, 20));
        assertThrows(BusinessException.class, () -> service.searchOrders(filter(null, null, now, now.minusSeconds(1)), 0, 20));
    }

    @Test
    void theShopDetailHasTheSnapshotThePaymentsAndTheStatusHistory() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order(ORDER_ID, Instant.now())));
        when(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(OrderItem.builder().id(UUID.randomUUID()).orderId(ORDER_ID)
                .productName("RAM").quantity(2).unitPrice(500L).discountAmount(0L).build()));
        PaymentResponse attempt = new PaymentResponse(UUID.randomUUID(), ORDER_ID, UUID.randomUUID(), 10L, "PENDING", null, Instant.now());
        when(paymentClient.getPaymentsByOrder(ORDER_ID, TOKEN)).thenReturn(ApiResponse.success(List.of(attempt)));
        when(historyRepository.findByOrderIdOrderByCreatedAtAsc(ORDER_ID)).thenReturn(List.of(
                OrderStatusHistory.builder().toStatus(OrderStatus.PENDING_PAYMENT).build(),
                OrderStatusHistory.builder().fromStatus(OrderStatus.PENDING_PAYMENT).toStatus(OrderStatus.PENDING_CONFIRMATION).build()));

        AdminOrderDetailResponse detail = service.getOrderDetail(ORDER_ID, TOKEN);

        assertEquals(CUSTOMER, detail.customerId());
        assertEquals(1000L, detail.items().getFirst().lineTotal());
        assertEquals(List.of(attempt), detail.payments());
        assertEquals(2, detail.statusHistory().size());
        assertNull(detail.statusHistory().getFirst().fromStatus());
    }

    @Test
    void theShopDetailOfAnUnknownOrderIsNotFound() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getOrderDetail(ORDER_ID, TOKEN));
    }
}
