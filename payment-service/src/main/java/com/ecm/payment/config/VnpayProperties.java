package com.ecm.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The merchant settings VNPAY gives for the sandbox (or for production). {@code returnUrl} is the page of the
 * storefront the customer comes back to; it must be one the browser can open.
 */
@ConfigurationProperties(prefix = "vnpay")
public record VnpayProperties(String tmnCode, String hashSecret, String payUrl, String returnUrl, int expireMinutes) {

    public boolean isConfigured() {
        return tmnCode != null && !tmnCode.isBlank() && hashSecret != null && !hashSecret.isBlank();
    }
}
