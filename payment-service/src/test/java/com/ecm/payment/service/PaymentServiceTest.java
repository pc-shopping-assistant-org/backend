package com.ecm.payment.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.InvalidStateException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.ApiResponse;
import com.ecm.common.response.PageResponse;
import com.ecm.payment.client.IdentityServiceClient;
import com.ecm.payment.dto.request.AdminPaymentSearchRequest;
import com.ecm.payment.dto.request.CreatePaymentRequest;
import com.ecm.payment.dto.response.PaymentResponse;
import com.ecm.payment.dto.request.PaymentWebhookRequest;
import com.ecm.payment.dto.response.AdminPaymentResponse;
import com.ecm.payment.entity.OutboxEvent;
import com.ecm.payment.entity.Payment;
import com.ecm.payment.entity.PaymentMethod;
import com.ecm.payment.entity.PaymentStatus;
import com.ecm.payment.exception.PaymentErrorCode;
import com.ecm.payment.mapper.PaymentMapper;
import com.ecm.payment.repository.OutboxEventRepository;
import com.ecm.payment.repository.PaymentMethodRepository;
import com.ecm.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final UUID COD_METHOD = UUID.randomUUID();
    private static final UUID CARD_METHOD = UUID.randomUUID();
    private static final String TOKEN = "Bearer t";

    private PaymentRepository paymentRepository;
    private PaymentMethodRepository methodRepository;
    private OutboxEventRepository outboxRepository;
    private IdentityServiceClient identityClient;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        methodRepository = mock(PaymentMethodRepository.class);
        outboxRepository = mock(OutboxEventRepository.class);
        identityClient = mock(IdentityServiceClient.class);
        service = new PaymentService(paymentRepository, methodRepository, outboxRepository, identityClient,
                Mappers.getMapper(PaymentMapper.class), new ObjectMapper());
        when(methodRepository.findById(COD_METHOD)).thenReturn(Optional.of(PaymentMethod.builder().id(COD_METHOD).code("COD").build()));
        when(methodRepository.findById(CARD_METHOD)).thenReturn(Optional.of(PaymentMethod.builder().id(CARD_METHOD).code("ONLINE_CARD").build()));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(call -> call.getArgument(0));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(call -> call.getArgument(0));
    }

    private Payment payment(UUID method, PaymentStatus status) {
        Payment payment = Payment.builder().id(PAYMENT_ID).orderId(ORDER_ID).customerId(CUSTOMER).paymentMethodId(method).amount(100L).status(status).build();
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        return payment;
    }

    // ---- create ----

    @Test
    void aNewPaymentIsPendingAndKeepsTheCustomerOfTheOrder() {
        PaymentResponse response = service.create(new CreatePaymentRequest(ORDER_ID, CUSTOMER, CARD_METHOD, 100L, "key"));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertEquals(CUSTOMER, saved.getValue().getCustomerId());
        assertEquals(PaymentStatus.PENDING, response.status());
    }

    @Test
    void aRetriedCallWithTheSameKeyReturnsTheExistingPayment() {
        Payment existing = Payment.builder().id(PAYMENT_ID).orderId(ORDER_ID).status(PaymentStatus.PENDING).build();
        when(paymentRepository.findByIdempotencyKey("key")).thenReturn(Optional.of(existing));

        assertEquals(PAYMENT_ID, service.create(new CreatePaymentRequest(ORDER_ID, CUSTOMER, CARD_METHOD, 100L, "key")).id());

        verify(paymentRepository, never()).save(any());
    }

    // ---- reading ----

    @Test
    void aCustomerReadsOnlyTheirOwnPaymentsAndAnEmployeeReadsAll() {
        service.getPaymentsByOrder(ORDER_ID, CUSTOMER);
        service.getPaymentsByOrder(ORDER_ID, null);

        verify(paymentRepository).findByOrderIdAndCustomerIdOrderByCreatedAtAsc(ORDER_ID, CUSTOMER);
        verify(paymentRepository).findByOrderIdOrderByCreatedAtAsc(ORDER_ID);
    }

    // ---- webhook ----

    @Test
    void aPaidWebhookMarksTheOnlinePaymentPaidAndQueuesTheEvent() {
        Payment payment = payment(CARD_METHOD, PaymentStatus.PENDING);

        service.handleWebhook(PAYMENT_ID, new PaymentWebhookRequest("PAID", "TX-1"));

        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertNotNull(payment.getPaidAt());
        assertEquals("TX-1", payment.getProviderTransactionCode());
        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(event.capture());
        assertEquals("payment.completed", event.getValue().getDestination());
    }

    @Test
    void aFailedWebhookMarksTheOnlinePaymentFailed() {
        Payment payment = payment(CARD_METHOD, PaymentStatus.PENDING);

        service.handleWebhook(PAYMENT_ID, new PaymentWebhookRequest("FAILED", "TX-2"));

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertNull(payment.getPaidAt());
    }

    @Test
    void aCashOnDeliveryPaymentIsNeverSettledByTheGateway() {
        payment(COD_METHOD, PaymentStatus.PENDING);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.handleWebhook(PAYMENT_ID, new PaymentWebhookRequest("PAID", "TX")));

        assertEquals(PaymentErrorCode.WEBHOOK_NOT_ALLOWED, error.getErrorCode());
    }

    @Test
    void aFinishedPaymentCannotBeNotifiedAgain() {
        payment(CARD_METHOD, PaymentStatus.CANCELLED);

        assertThrows(InvalidStateException.class, () -> service.handleWebhook(PAYMENT_ID, new PaymentWebhookRequest("PAID", "TX")));
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void aTransactionCodeAlreadyUsedByAnotherPaymentIsAConflictNotAServerError() {
        payment(CARD_METHOD, PaymentStatus.PENDING);
        when(paymentRepository.existsByProviderTransactionCodeAndIdNot("TX-1", PAYMENT_ID)).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.handleWebhook(PAYMENT_ID, new PaymentWebhookRequest("PAID", "TX-1")));

        assertEquals(PaymentErrorCode.DUPLICATE_TRANSACTION_CODE, error.getErrorCode());
    }

    // ---- order cancelled ----

    @Test
    void aCancelledOrderCancelsItsPendingPaymentsOnly() {
        Payment pending = Payment.builder().status(PaymentStatus.PENDING).build();
        when(paymentRepository.findByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(List.of(pending));

        service.cancelPendingPayments(ORDER_ID);

        assertEquals(PaymentStatus.CANCELLED, pending.getStatus());
        verify(paymentRepository, never()).findByOrderIdAndStatus(ORDER_ID, PaymentStatus.PAID);
    }

    // ---- UC-ADM-PAY-002 status by hand ----

    @Test
    void theShopMarksACashOnDeliveryPaymentPaidWhenTheCashIsCollected() {
        Payment payment = payment(COD_METHOD, PaymentStatus.PENDING);

        AdminPaymentResponse response = service.updateStatus(PAYMENT_ID, PaymentStatus.PAID, EMPLOYEE);

        assertEquals(PaymentStatus.PAID, response.status());
        assertNotNull(payment.getPaidAt());
        assertEquals(EMPLOYEE, payment.getUpdatedBy());
    }

    @Test
    void theShopMarksAPaidPaymentRefundedOfAnyMethodAndKeepsWhenItWasPaid() {
        for (UUID method : List.of(COD_METHOD, CARD_METHOD)) {
            Payment payment = payment(method, PaymentStatus.PAID);
            Instant paidAt = Instant.parse("2026-01-01T00:00:00Z");
            payment.setPaidAt(paidAt);

            assertEquals(PaymentStatus.REFUNDED, service.updateStatus(PAYMENT_ID, PaymentStatus.REFUNDED, EMPLOYEE).status());
            assertEquals(paidAt, payment.getPaidAt());
            assertEquals(EMPLOYEE, payment.getUpdatedBy());
        }
    }

    @Test
    void theShopCannotMarkAnOnlinePaymentPaidByHand() {
        Payment payment = payment(CARD_METHOD, PaymentStatus.PENDING);

        assertThrows(BusinessException.class, () -> service.updateStatus(PAYMENT_ID, PaymentStatus.PAID, EMPLOYEE));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    void everythingElseIsRejected() {
        for (PaymentStatus from : PaymentStatus.values()) {
            for (PaymentStatus to : PaymentStatus.values()) {
                boolean refund = from == PaymentStatus.PAID && to == PaymentStatus.REFUNDED;
                if (refund) {
                    continue;
                }
                // a cash-on-delivery PENDING to PAID is the other allowed change; the online method allows none
                payment(CARD_METHOD, from);
                assertThrows(BusinessException.class, () -> service.updateStatus(PAYMENT_ID, to, EMPLOYEE), from + " to " + to);
            }
        }
        payment(COD_METHOD, PaymentStatus.FAILED);
        assertThrows(BusinessException.class, () -> service.updateStatus(PAYMENT_ID, PaymentStatus.PAID, EMPLOYEE));
        payment(COD_METHOD, PaymentStatus.PENDING);
        assertThrows(BusinessException.class, () -> service.updateStatus(PAYMENT_ID, PaymentStatus.REFUNDED, EMPLOYEE));
    }

    @Test
    void anUnknownPaymentIsNotFound() {
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(PAYMENT_ID, PaymentStatus.PAID, EMPLOYEE));
    }

    // ---- UC-ADM-PAY-003 search ----

    private static AdminPaymentSearchRequest filter(String code, UUID customerId, String customerName, UUID orderId, PaymentStatus status) {
        return new AdminPaymentSearchRequest(code, customerId, customerName, orderId, status, null, null);
    }

    private void searchReturns(Payment... payments) {
        when(paymentRepository.search(anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(), anyString(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(payments)));
    }

    @Test
    void withoutFiltersEveryPaymentIsSearched() {
        searchReturns(Payment.builder().id(PAYMENT_ID).customerId(CUSTOMER).status(PaymentStatus.PAID).build());

        PageResponse<AdminPaymentResponse> page = service.searchPayments(filter(null, null, null, null, null), 0, 20, TOKEN);

        assertEquals(CUSTOMER, page.getContent().getFirst().customerId());
        verify(paymentRepository).search(eq(true), any(), eq(true), any(), eq(true), any(), eq(""), eq(Instant.EPOCH), any(), any());
        verifyNoInteractions(identityClient);
    }

    @Test
    void theTransactionCodeStatusAndCustomerNarrowTheSearch() {
        searchReturns();

        service.searchPayments(filter(" tx-1 ", CUSTOMER, null, ORDER_ID, PaymentStatus.PAID), 0, 20, TOKEN);

        ArgumentCaptor<Collection<UUID>> customers = ArgumentCaptor.forClass(Collection.class);
        verify(paymentRepository).search(eq(false), eq(PaymentStatus.PAID), eq(false), eq(ORDER_ID), eq(false), customers.capture(),
                eq("tx-1"), any(), any(), any());
        assertEquals(List.of(CUSTOMER), List.copyOf(customers.getValue()));
    }

    @Test
    void aCustomerNameIsResolvedByTheIdentityService() {
        UUID found = UUID.randomUUID();
        when(identityClient.findCustomerIds("an", TOKEN)).thenReturn(ApiResponse.success(List.of(found)));
        searchReturns();

        service.searchPayments(filter(null, null, " an ", null, null), 0, 20, TOKEN);

        ArgumentCaptor<Collection<UUID>> customers = ArgumentCaptor.forClass(Collection.class);
        verify(paymentRepository).search(anyBoolean(), any(), anyBoolean(), any(), eq(false), customers.capture(), anyString(), any(), any(), any());
        assertEquals(List.of(found), List.copyOf(customers.getValue()));
    }

    @Test
    void aNameThatMatchesNobodyFindsNothingWithoutQueryingPayments() {
        when(identityClient.findCustomerIds("zzz", TOKEN)).thenReturn(ApiResponse.success(List.of()));

        assertTrue(service.searchPayments(filter(null, null, "zzz", null, null), 0, 20, TOKEN).getContent().isEmpty());

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void aNameAndACustomerIdTogetherMustAgree() {
        when(identityClient.findCustomerIds("an", TOKEN)).thenReturn(ApiResponse.success(List.of(UUID.randomUUID())));

        assertTrue(service.searchPayments(filter(null, CUSTOMER, "an", null, null), 0, 20, TOKEN).getContent().isEmpty());
    }

    @Test
    void aBadPageOrPeriodIsRejected() {
        Instant now = Instant.now();

        assertThrows(BusinessException.class, () -> service.searchPayments(filter(null, null, null, null, null), -1, 20, TOKEN));
        assertThrows(BusinessException.class, () -> service.searchPayments(filter(null, null, null, null, null), 0, 101, TOKEN));
        assertThrows(BusinessException.class,
                () -> service.searchPayments(new AdminPaymentSearchRequest(null, null, null, null, null, now, now), 0, 20, TOKEN));
    }
}
