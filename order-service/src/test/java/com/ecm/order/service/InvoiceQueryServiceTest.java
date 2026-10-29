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

class InvoiceQueryServiceTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID ORDER_ID = UUID.fromString("0192f3a4-1b2c-7d3e-8f40-516273849abc");
    private static final String TOKEN = "Bearer token";

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private PaymentServiceClient paymentClient;
    private OrderStatusHistoryRepository historyRepository;
    private InvoiceQueryService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        paymentClient = mock(PaymentServiceClient.class);
        historyRepository = mock(OrderStatusHistoryRepository.class);
        service = new InvoiceQueryService(orderRepository, orderItemRepository, Mappers.getMapper(OrderMapper.class));
        when(orderItemRepository.findByOrderIdIn(anyCollection())).thenReturn(List.of());
    }

    private static Order order(UUID id, Instant createdAt) {
        return Order.builder().id(id).customerId(CUSTOMER).invoiceNumber("INV-ABCDEFGH23").status(OrderStatus.PENDING_PAYMENT)
                .totalAmount(100L).createdAt(createdAt).build();
    }

    // ---- UC-ADM-INV-001 / 002 invoices ----

    @Test
    void anInvoiceRowShowsTheInvoiceNumberTheCustomerAndTheDeliveryDateAsTheInvoiceDate() {
        Instant delivered = Instant.parse("2026-10-01T10:00:00Z");
        Order order = order(ORDER_ID, Instant.now());
        order.setStatus(OrderStatus.COMPLETED);
        order.setDeliveredAt(delivered);
        order.setRecipientName("Nguyen Van A");
        when(orderRepository.searchInvoices(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(order)));

        InvoiceSummaryResponse row = service.searchInvoices(new AdminInvoiceSearchRequest(null, null, null), 0, 20).getContent().getFirst();

        assertEquals("INV-ABCDEFGH23", row.invoiceNumber());
        assertEquals("Nguyen Van A", row.recipientName());
        assertEquals(delivered, row.invoiceDate());
    }

    @Test
    void theInvoiceSearchTrimsTheKeywordAndLeavesTheDatesOpen() {
        when(orderRepository.searchInvoices(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.searchInvoices(new AdminInvoiceSearchRequest("  inv-1 ", null, null), 1, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).searchInvoices(eq("inv-1"), eq(Instant.EPOCH), any(), pageable.capture());
        assertEquals(1, pageable.getValue().getPageNumber());
    }

    @Test
    void aBadInvoicePageOrPeriodIsRejected() {
        Instant now = Instant.now();

        assertThrows(BusinessException.class, () -> service.searchInvoices(new AdminInvoiceSearchRequest(null, null, null), 0, 101));
        assertThrows(BusinessException.class, () -> service.searchInvoices(new AdminInvoiceSearchRequest(null, now, now), 0, 20));
    }

    @Test
    void anInvoiceHasTheSnapshotOfItsCompletedOrder() {
        Order order = order(ORDER_ID, Instant.now());
        order.setStatus(OrderStatus.COMPLETED);
        order.setDeliveredAt(Instant.parse("2026-10-01T10:00:00Z"));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(OrderItem.builder().id(UUID.randomUUID()).orderId(ORDER_ID)
                .productName("RAM").quantity(2).unitPrice(500L).discountAmount(0L).build()));

        InvoiceDetailResponse invoice = service.getInvoice(ORDER_ID);

        assertEquals(ORDER_ID, invoice.id());
        assertEquals(Instant.parse("2026-10-01T10:00:00Z"), invoice.invoiceDate());
        assertEquals(1000L, invoice.items().getFirst().lineTotal());
    }

    @Test
    void anOrderThatIsNotCompletedHasNoInvoice() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order(ORDER_ID, Instant.now())));

        assertThrows(ResourceNotFoundException.class, () -> service.getInvoice(ORDER_ID));
    }
}
