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
import com.ecm.order.mapper.OrderMapper;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderQueryServiceTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID ORDER_ID = UUID.fromString("0192f3a4-1b2c-7d3e-8f40-516273849abc");
    private static final String TOKEN = "Bearer token";

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private PaymentServiceClient paymentClient;
    private OrderQueryService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        paymentClient = mock(PaymentServiceClient.class);
        service = new OrderQueryService(orderRepository, orderItemRepository, paymentClient, Mappers.getMapper(OrderMapper.class));
        when(orderItemRepository.findByOrderIdIn(anyCollection())).thenReturn(List.of());
    }

    private static Order order(UUID id, Instant createdAt) {
        return Order.builder().id(id).customerId(CUSTOMER).invoiceNumber("INV-ABCDEFGH23").status(OrderStatus.PENDING_PAYMENT)
                .totalAmount(100L).createdAt(createdAt).build();
    }

    private List<Order> orders(int count) {
        List<Order> list = new ArrayList<>();
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        for (int i = 0; i < count; i++) {
            list.add(order(UUID.randomUUID(), now.minusSeconds(i)));
        }
        return list;
    }

    private void listReturns(List<Order> rows) {
        when(orderRepository.findCustomerPage(any(), anyBoolean(), any(), anyBoolean(), any(), anyString(), any(), any(), any())).thenReturn(rows);
    }

    private ArgumentCaptor<Pageable> verifyListQuery(boolean anyStatus, boolean anyKeyword, UUID orderId, String invoice) {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findCustomerPage(eq(CUSTOMER), eq(anyStatus), any(), eq(anyKeyword), eq(orderId), eq(invoice), any(), any(), pageable.capture());
        return pageable;
    }

    // ---- UC-ORD-004 history ----

    @Test
    void readsOneExtraRowAndReturnsACursorOnlyWhenAnotherPageFollows() {
        List<Order> three = orders(3);
        listReturns(three);

        CursorPageResponse<OrderSummaryResponse> page = service.getOrders(CUSTOMER, null, null, null, 2);

        assertTrue(page.hasNext());
        assertEquals(2, page.items().size());
        assertNotNull(page.nextCursor());
        assertEquals(3, verifyListQuery(true, true, new UUID(0L, 0L), "").getValue().getPageSize());
    }

    @Test
    void theLastPageHasNoCursor() {
        listReturns(orders(2));

        CursorPageResponse<OrderSummaryResponse> page = service.getOrders(CUSTOMER, null, null, null, 5);

        assertFalse(page.hasNext());
        assertNull(page.nextCursor());
    }

    @Test
    void theCursorOfOnePageStartsTheNextAfterTheLastOrderSeen() {
        List<Order> three = orders(3);
        listReturns(three);
        String cursor = service.getOrders(CUSTOMER, null, null, null, 2).nextCursor();

        service.getOrders(CUSTOMER, null, null, cursor, 2);

        ArgumentCaptor<Instant> createdAt = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
        verify(orderRepository, org.mockito.Mockito.times(2)).findCustomerPage(any(), anyBoolean(), any(), anyBoolean(), any(), anyString(),
                createdAt.capture(), id.capture(), any());
        assertEquals(three.get(1).getCreatedAt(), createdAt.getAllValues().get(1));
        assertEquals(three.get(1).getId(), id.getAllValues().get(1));
    }

    @Test
    void aBadCursorIsRejected() {
        assertThrows(BusinessException.class, () -> service.getOrders(CUSTOMER, null, null, "not-a-cursor", 10));
    }

    @Test
    void pageSizeIsDefaultedAndCapped() {
        listReturns(List.of());

        service.getOrders(CUSTOMER, null, null, null, 10_000);
        service.getOrders(CUSTOMER, null, null, null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository, org.mockito.Mockito.times(2)).findCustomerPage(any(), anyBoolean(), any(), anyBoolean(), any(), anyString(),
                any(), any(), pageable.capture());
        assertEquals(101, pageable.getAllValues().get(0).getPageSize());
        assertEquals(21, pageable.getAllValues().get(1).getPageSize());
    }

    @Test
    void theStatusFilterIsPassedOn() {
        listReturns(List.of());

        service.getOrders(CUSTOMER, OrderStatus.CANCELLED, null, null, 10);

        verify(orderRepository).findCustomerPage(eq(CUSTOMER), eq(false), eq(OrderStatus.CANCELLED), anyBoolean(), any(), anyString(), any(), any(), any());
    }

    // ---- UC-ORD-005 search ----

    @Test
    void aDashedUuidSearchesByOrderIdAndAnInvoiceNumberSearchesByInvoiceNumber() {
        listReturns(List.of());

        service.getOrders(CUSTOMER, null, " " + ORDER_ID + " ", null, 10);
        verifyListQuery(true, false, ORDER_ID, ORDER_ID.toString().toUpperCase());
    }

    @Test
    void anUndashedUuidIsAcceptedToo() {
        listReturns(List.of());

        service.getOrders(CUSTOMER, null, ORDER_ID.toString().replace("-", ""), null, 10);

        verifyListQuery(true, false, ORDER_ID, ORDER_ID.toString().replace("-", "").toUpperCase());
    }

    @Test
    void anInvoiceNumberIsMatchedCaseInsensitively() {
        listReturns(List.of());

        service.getOrders(CUSTOMER, null, "inv-7k3m9qx2bd", null, 10);

        verifyListQuery(true, false, new UUID(0L, 0L), "INV-7K3M9QX2BD");
    }

    @Test
    void aBlankKeywordMeansNoSearch() {
        listReturns(List.of());

        service.getOrders(CUSTOMER, null, "   ", null, 10);

        verifyListQuery(true, true, new UUID(0L, 0L), "");
    }

    @Test
    void theSummaryShowsTheNumberOfLinesAndTheFirstProduct() {
        Order order = orders(1).getFirst();
        listReturns(List.of(order));
        when(orderItemRepository.findByOrderIdIn(anyCollection())).thenReturn(List.of(
                OrderItem.builder().orderId(order.getId()).productName("RAM Kit").build(),
                OrderItem.builder().orderId(order.getId()).productName("SSD").build()));

        OrderSummaryResponse summary = service.getOrders(CUSTOMER, null, null, null, 10).items().getFirst();

        assertEquals(2, summary.itemCount());
        assertEquals("RAM Kit", summary.firstProductName());
        assertEquals("INV-ABCDEFGH23", summary.invoiceNumber());
    }

    // ---- UC-ORD-002 status / UC-ORD-006 detail ----

    @Test
    void statusAndDetailOfAnotherCustomerOrderDoNotExist() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getStatus(ORDER_ID, CUSTOMER));
        assertThrows(ResourceNotFoundException.class, () -> service.getDetail(ORDER_ID, CUSTOMER, TOKEN));
    }

    @Test
    void statusIsReturnedWithoutCallingThePaymentService() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(Optional.of(order(ORDER_ID, Instant.now())));

        assertEquals(OrderStatus.PENDING_PAYMENT, service.getStatus(ORDER_ID, CUSTOMER).status());

        org.mockito.Mockito.verifyNoInteractions(paymentClient);
    }

    @Test
    void detailShowsTheSnapshotLinesAndThePaymentAttempts() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(Optional.of(order(ORDER_ID, Instant.now())));
        when(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(OrderItem.builder().id(UUID.randomUUID()).orderId(ORDER_ID)
                .productName("RAM Kit").sku("SKU-1").variantLabel("Color: Blue").quantity(2).unitPrice(500L).discountAmount(100L).build()));
        PaymentResponse attempt = new PaymentResponse(UUID.randomUUID(), ORDER_ID, UUID.randomUUID(), 100L, "PENDING", null, Instant.now());
        when(paymentClient.getPaymentsByOrder(ORDER_ID, TOKEN)).thenReturn(ApiResponse.success(List.of(attempt)));

        OrderDetailResponse detail = service.getDetail(ORDER_ID, CUSTOMER, TOKEN);

        assertEquals(900L, detail.items().getFirst().lineTotal());
        assertEquals("Color: Blue", detail.items().getFirst().variantLabel());
        assertEquals(List.of(attempt), detail.payments());
    }

    @Test
    void detailFailsWhenThePaymentServiceCannotBeReached() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(Optional.of(order(ORDER_ID, Instant.now())));
        when(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(paymentClient.getPaymentsByOrder(ORDER_ID, TOKEN)).thenThrow(mock(FeignException.class));

        assertThrows(ExternalServiceException.class, () -> service.getDetail(ORDER_ID, CUSTOMER, TOKEN));
    }
}
