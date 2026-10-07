package com.ecm.order.dto.response;

import java.time.LocalDate;

/**
 * Revenue of one day, or of one month ({@code period} is then its first day).
 */
public record RevenuePointResponse(LocalDate period, long revenue, long orderCount) {
}
