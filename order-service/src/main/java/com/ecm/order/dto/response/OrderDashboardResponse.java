package com.ecm.order.dto.response;

import com.ecm.order.entity.OrderStatus;

import java.util.Map;

/**
 * The order side of the admin dashboard. Revenue is the total of the orders completed (delivered) today and
 * this month, in the shop's time zone; {@code ordersByStatus} has an entry for every status.
 */
public record OrderDashboardResponse(
        long revenueToday,
        long revenueThisMonth,
        Map<OrderStatus, Long> ordersByStatus) {
}
