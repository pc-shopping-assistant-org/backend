package com.ecm.payment.controller;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.response.ApiResponse;
import com.ecm.common.security.CurrentUser;
import com.ecm.payment.dto.response.VnpayResultResponse;
import com.ecm.payment.dto.response.VnpayUrlResponse;
import com.ecm.payment.exception.PaymentErrorCode;
import com.ecm.payment.service.VnpayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Paying through VNPAY: the customer asks for the pay URL; VNPAY calls back on the IPN and the customer returns. */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class VnpayController {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String IPV6_LOOPBACK = "0:0:0:0:0:0:0:1";
    private static final String IPV4_LOOPBACK = "127.0.0.1";
    private static final String INVALID_SIGNATURE_CODE = "97";
    private static final String INVALID_AMOUNT_CODE = "04";

    private final VnpayService vnpayService;

    /** The URL of the VNPAY page that takes the payment; only for the payment of the caller. */
    @PostMapping("/{id}/vnpay-url")
    public ApiResponse<VnpayUrlResponse> createPaymentUrl(@PathVariable UUID id, Authentication authentication, HttpServletRequest request) {
        return ApiResponse.success(vnpayService.createPaymentUrl(id, CurrentUser.accountId(authentication), clientIp(request)));
    }

    /** Called by VNPAY itself (public; the signature is the proof); it answers in VNPAY's own format, not ours. */
    @GetMapping("/vnpay/ipn")
    public Map<String, String> ipn(@RequestParam Map<String, String> params) {
        VnpayService.Outcome outcome = vnpayService.settle(params);
        return Map.of("RspCode", outcome.rspCode(), "Message", outcome.message());
    }

    /** Where the customer lands after VNPAY: the storefront asks what became of the payment. */
    @GetMapping("/vnpay/return")
    public ApiResponse<VnpayResultResponse> returned(@RequestParam Map<String, String> params) {
        VnpayService.Outcome outcome = vnpayService.settle(params);
        if (outcome.result() == null) {
            throw new BusinessException(switch (outcome.rspCode()) {
                case INVALID_SIGNATURE_CODE -> PaymentErrorCode.INVALID_VNPAY_SIGNATURE;
                case INVALID_AMOUNT_CODE -> PaymentErrorCode.VNPAY_AMOUNT_MISMATCH;
                default -> PaymentErrorCode.PAYMENT_NOT_PAYABLE;
            });
        }
        return ApiResponse.success(outcome.result());
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        String ip = forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        return IPV6_LOOPBACK.equals(ip) ? IPV4_LOOPBACK : ip;
    }
}
