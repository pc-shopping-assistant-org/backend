package com.ecm.payment.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.payment.config.VnpayProperties;
import com.ecm.payment.dto.response.VnpayUrlResponse;
import com.ecm.payment.entity.Payment;
import com.ecm.payment.entity.PaymentMethod;
import com.ecm.payment.entity.PaymentStatus;
import com.ecm.payment.exception.PaymentErrorCode;
import com.ecm.payment.repository.PaymentMethodRepository;
import com.ecm.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import static org.mockito.Mockito.when;

class VnpayServiceTest {

    private static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PAYMENT_ID = UUID.fromString("01a11bf1-ea29-7f73-bc64-f0e97eb369c3");
    private static final UUID ORDER_ID = UUID.fromString("01a11bf1-e8f0-7d5e-9a3b-5f0c44a1d2e7");
    private static final UUID METHOD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final VnpayProperties properties = new VnpayProperties("TMNTEST1", "test-secret-key", PAY_URL, "http://localhost:3000/vi/payment/vnpay-return", 15);
    private final VnpaySigner signer = new VnpaySigner(properties);
    private PaymentRepository paymentRepository;
    private PaymentMethodRepository methodRepository;
    private PaymentService paymentService;
    private VnpayService service;
    private Payment payment;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        methodRepository = mock(PaymentMethodRepository.class);
        paymentService = mock(PaymentService.class);
        service = new VnpayService(properties, signer, paymentRepository, methodRepository, paymentService);
        payment = Payment.builder().id(PAYMENT_ID).orderId(ORDER_ID).customerId(CUSTOMER).paymentMethodId(METHOD_ID)
                .amount(2_310_000L).status(PaymentStatus.PENDING).build();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(methodRepository.findById(METHOD_ID)).thenReturn(Optional.of(PaymentMethod.builder().id(METHOD_ID).code("VNPAY").name("VNPAY").build()));
        when(paymentService.settleOnline(any(), anyBoolean(), any(), anyString())).thenAnswer(call -> {
            Payment settled = call.getArgument(0);
            settled.setStatus(call.getArgument(1) ? PaymentStatus.PAID : PaymentStatus.FAILED);
            return settled;
        });
    }

    private Map<String, String> parse(String url) {
        Map<String, String> params = new HashMap<>();
        for (String pair : url.substring(url.indexOf('?') + 1).split("&")) {
            String[] keyValue = pair.split("=", 2);
            params.put(keyValue[0], URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    /** What VNPAY sends back: the request fields it was given, plus its outcome, signed with the shared secret. */
    private Map<String, String> callback(String responseCode, String transactionStatus, String amount) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", "TMNTEST1");
        params.put("vnp_Amount", amount);
        params.put("vnp_TxnRef", PAYMENT_ID.toString().replace("-", "") + "20261008213000");
        params.put("vnp_OrderInfo", "Thanh toan don hang");
        params.put("vnp_TransactionNo", "14123456");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", transactionStatus);
        params.put("vnp_SecureHash", parse("?" + signer.signedQuery(params)).get("vnp_SecureHash"));
        return params;
    }

    // ---- the pay URL ----

    @Test
    void thePayUrlCarriesTheAmountInHundredthsAndAValidSignature() {
        VnpayUrlResponse response = service.createPaymentUrl(PAYMENT_ID, CUSTOMER, "127.0.0.1");

        assertTrue(response.paymentUrl().startsWith(PAY_URL + "?"));
        Map<String, String> params = parse(response.paymentUrl());
        assertEquals("231000000", params.get("vnp_Amount"));
        assertEquals("TMNTEST1", params.get("vnp_TmnCode"));
        assertEquals("VND", params.get("vnp_CurrCode"));
        assertEquals("127.0.0.1", params.get("vnp_IpAddr"));
        assertEquals("http://localhost:3000/vi/payment/vnpay-return", params.get("vnp_ReturnUrl"));
        assertTrue(params.get("vnp_TxnRef").startsWith(PAYMENT_ID.toString().replace("-", "")));
        assertTrue(signer.verify(params));
    }

    @Test
    void thePayUrlIsRefusedWhenVnpayIsNotConfigured() {
        VnpayProperties unset = new VnpayProperties("", "", PAY_URL, "http://r", 15);
        VnpayService unconfigured = new VnpayService(unset, new VnpaySigner(unset), paymentRepository, methodRepository, paymentService);

        BusinessException ex = assertThrows(BusinessException.class, () -> unconfigured.createPaymentUrl(PAYMENT_ID, CUSTOMER, "127.0.0.1"));

        assertEquals(PaymentErrorCode.VNPAY_NOT_CONFIGURED, ex.getErrorCode());
    }

    @Test
    void anotherCustomerCannotPayThePayment() {
        assertThrows(ResourceNotFoundException.class, () -> service.createPaymentUrl(PAYMENT_ID, UUID.randomUUID(), "127.0.0.1"));
    }

    @Test
    void aPaymentThatIsNotPendingOrNotForVnpayIsNotPayable() {
        payment.setStatus(PaymentStatus.PAID);
        assertEquals(PaymentErrorCode.PAYMENT_NOT_PAYABLE,
                assertThrows(BusinessException.class, () -> service.createPaymentUrl(PAYMENT_ID, CUSTOMER, "127.0.0.1")).getErrorCode());

        payment.setStatus(PaymentStatus.PENDING);
        when(methodRepository.findById(METHOD_ID)).thenReturn(Optional.of(PaymentMethod.builder().id(METHOD_ID).code("COD").name("COD").build()));
        assertEquals(PaymentErrorCode.PAYMENT_NOT_PAYABLE,
                assertThrows(BusinessException.class, () -> service.createPaymentUrl(PAYMENT_ID, CUSTOMER, "127.0.0.1")).getErrorCode());
    }

    // ---- the callback ----

    @Test
    void aSuccessfulCallbackPaysThePayment() {
        VnpayService.Outcome outcome = service.settle(callback("00", "00", "231000000"));

        verify(paymentService).settleOnline(eq(payment), eq(true), eq("14123456"), anyString());
        assertEquals("00", outcome.rspCode());
        assertEquals("PAID", outcome.result().result());
        assertEquals(ORDER_ID, outcome.result().orderId());
    }

    @Test
    void aFailedCallbackFailsThePayment() {
        VnpayService.Outcome outcome = service.settle(callback("51", "02", "231000000"));

        verify(paymentService).settleOnline(eq(payment), eq(false), any(), anyString());
        assertEquals("FAILED", outcome.result().result());
    }

    @Test
    void aCustomerWhoLeavesTheVnpayPageKeepsAPayablePayment() {
        VnpayService.Outcome outcome = service.settle(callback("24", "02", "231000000"));

        verify(paymentService, never()).settleOnline(any(), anyBoolean(), any(), anyString());
        assertEquals("00", outcome.rspCode());
        assertEquals("CANCELLED", outcome.result().result());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    void aCallbackWithABadSignatureOrAmountChangesNothing() {
        Map<String, String> tampered = callback("00", "00", "231000000");
        tampered.put("vnp_Amount", "100");
        assertEquals("97", service.settle(tampered).rspCode());

        assertEquals("04", service.settle(callback("00", "00", "100")).rspCode());

        Map<String, String> unsigned = callback("00", "00", "231000000");
        unsigned.remove("vnp_SecureHash");
        assertEquals("97", service.settle(unsigned).rspCode());
        verify(paymentService, never()).settleOnline(any(), anyBoolean(), any(), anyString());
    }

    @Test
    void aTransactionNumberAnotherPaymentAlreadyHasIsAnsweredAndChangesNothing() {
        when(paymentRepository.existsByProviderTransactionCodeAndIdNot("14123456", PAYMENT_ID)).thenReturn(true);

        VnpayService.Outcome outcome = service.settle(callback("00", "00", "231000000"));

        assertEquals("02", outcome.rspCode());
        assertNull(outcome.result());
        verify(paymentService, never()).settleOnline(any(), anyBoolean(), any(), anyString());
    }

    @Test
    void aCallbackForAnUnknownOrAlreadySettledPaymentIsAnswered() {
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.empty());
        VnpayService.Outcome unknown = service.settle(callback("00", "00", "231000000"));
        assertEquals("01", unknown.rspCode());
        assertNull(unknown.result());

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        payment.setStatus(PaymentStatus.PAID);
        VnpayService.Outcome again = service.settle(callback("00", "00", "231000000"));
        assertEquals("02", again.rspCode());
        assertEquals("PAID", again.result().result());
        assertFalse(again.result().result().isEmpty());
        verify(paymentService, never()).settleOnline(any(), anyBoolean(), any(), anyString());
    }
}
