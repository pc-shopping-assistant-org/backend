package com.ecm.payment.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.payment.config.VnpayProperties;
import com.ecm.payment.dto.response.VnpayResultResponse;
import com.ecm.payment.dto.response.VnpayUrlResponse;
import com.ecm.payment.entity.Payment;
import com.ecm.payment.entity.PaymentStatus;
import com.ecm.payment.exception.PaymentErrorCode;
import com.ecm.payment.repository.PaymentMethodRepository;
import com.ecm.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pays an online payment through VNPAY: builds the pay URL, and settles the payment from VNPAY's callback. */
@Service
@RequiredArgsConstructor
public class VnpayService {

    public static final String PAYMENT_METHOD_CODE = "VNPAY";
    public static final String RESULT_PAID = "PAID";
    public static final String RESULT_FAILED = "FAILED";
    public static final String RESULT_PENDING = "PENDING";
    public static final String RESULT_CANCELLED = "CANCELLED";

    private static final String VERSION = "2.1.0";
    private static final String COMMAND = "pay";
    private static final String CURRENCY = "VND";
    private static final String LOCALE = "vn";
    private static final String ORDER_TYPE = "other";
    private static final int AMOUNT_FACTOR = 100;
    private static final String SUCCESS = "00";
    private static final String USER_CANCELLED = "24";
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int PAYMENT_ID_HEX_LENGTH = 32;
    private static final String UUID_GROUPS = "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})";

    private final VnpayProperties properties;
    private final VnpaySigner signer;
    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentService paymentService;

    /** What VNPAY is told back: {@code rspCode}/{@code message} are its vocabulary, {@code result} is ours. */
    public record Outcome(String rspCode, String message, VnpayResultResponse result) {
    }

    @Transactional(readOnly = true)
    public VnpayUrlResponse createPaymentUrl(UUID paymentId, UUID customerId, String ipAddress) {
        // 1. VNPAY must be set up, and the payment must be the caller's own, still waiting, and meant for VNPAY
        if (!properties.isConfigured()) {
            throw new BusinessException(PaymentErrorCode.VNPAY_NOT_CONFIGURED);
        }
        Payment payment = paymentRepository.findById(paymentId)
                .filter(candidate -> candidate.getCustomerId().equals(customerId))
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        boolean viaVnpay = paymentMethodRepository.findById(payment.getPaymentMethodId())
                .map(method -> PAYMENT_METHOD_CODE.equals(method.getCode())).orElse(false);
        if (payment.getStatus() != PaymentStatus.PENDING || !viaVnpay) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_PAYABLE);
        }

        // 2. The signed pay URL; every request carries its own transaction reference, which starts with the payment id
        ZonedDateTime now = ZonedDateTime.now(VIETNAM);
        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_Version", VERSION);
        fields.put("vnp_Command", COMMAND);
        fields.put("vnp_TmnCode", properties.tmnCode());
        fields.put("vnp_Amount", String.valueOf(payment.getAmount() * AMOUNT_FACTOR));
        fields.put("vnp_CurrCode", CURRENCY);
        fields.put("vnp_TxnRef", paymentId.toString().replace("-", "") + TIME_FORMAT.format(now));
        fields.put("vnp_OrderInfo", "Thanh toan don hang " + payment.getOrderId());
        fields.put("vnp_OrderType", ORDER_TYPE);
        fields.put("vnp_Locale", LOCALE);
        fields.put("vnp_ReturnUrl", properties.returnUrl());
        fields.put("vnp_IpAddr", ipAddress);
        fields.put("vnp_CreateDate", TIME_FORMAT.format(now));
        fields.put("vnp_ExpireDate", TIME_FORMAT.format(now.plusMinutes(properties.expireMinutes())));
        return new VnpayUrlResponse(properties.payUrl() + "?" + signer.signedQuery(fields));
    }

    /** Settles the payment from a VNPAY callback (the customer's return or the server-to-server IPN); safe to call twice. */
    @Transactional
    public Outcome settle(Map<String, String> callback) {
        // 1. Only VNPAY can have signed it
        if (!properties.isConfigured() || !signer.verify(callback)) {
            return new Outcome("97", "Invalid signature", null);
        }

        // 2. The payment it is about, for the amount it asked
        Payment payment = findPayment(callback.get("vnp_TxnRef"));
        if (payment == null) {
            return new Outcome("01", "Payment not found", null);
        }
        if (!isAmount(callback.get("vnp_Amount"), payment.getAmount())) {
            return new Outcome("04", "Invalid amount", null);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return new Outcome("02", "Payment already confirmed", resultOf(payment, RESULT_FAILED));
        }

        String transactionNo = callback.get("vnp_TransactionNo");
        if (transactionNo != null && paymentRepository.existsByProviderTransactionCodeAndIdNot(transactionNo, payment.getId())) {
            return new Outcome("02", "Transaction already recorded", null);
        }

        // 3. Paid, abandoned by the customer (stays payable) or failed
        boolean paid = SUCCESS.equals(callback.get("vnp_ResponseCode")) && SUCCESS.equals(callback.get("vnp_TransactionStatus"));
        if (!paid && USER_CANCELLED.equals(callback.get("vnp_ResponseCode"))) {
            return new Outcome("00", "Confirm Success", resultOf(payment, RESULT_CANCELLED));
        }
        Payment settled = paymentService.settleOnline(payment, paid, transactionNo, "VNPAY reported failure");
        return new Outcome("00", "Confirm Success", resultOf(settled, RESULT_FAILED));
    }

    private Payment findPayment(String txnRef) {
        if (txnRef == null || txnRef.length() < PAYMENT_ID_HEX_LENGTH) {
            return null;
        }
        try {
            UUID id = UUID.fromString(txnRef.substring(0, PAYMENT_ID_HEX_LENGTH).replaceFirst(UUID_GROUPS, "$1-$2-$3-$4-$5"));
            return paymentRepository.findByIdForUpdate(id).orElse(null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isAmount(String vnpAmount, long expected) {
        try {
            return Long.parseLong(vnpAmount) == expected * AMOUNT_FACTOR;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /** {@code unpaid} is what a payment that is not paid and not pending counts as, or the cancelled marker for a pending one. */
    private static VnpayResultResponse resultOf(Payment payment, String unpaid) {
        String result = switch (payment.getStatus()) {
            case PAID -> RESULT_PAID;
            case PENDING -> RESULT_CANCELLED.equals(unpaid) ? RESULT_CANCELLED : RESULT_PENDING;
            default -> RESULT_FAILED;
        };
        return new VnpayResultResponse(payment.getId(), payment.getOrderId(), result, payment.getAmount());
    }
}
