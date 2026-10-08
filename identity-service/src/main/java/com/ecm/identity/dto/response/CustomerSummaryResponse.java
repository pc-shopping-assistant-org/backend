package com.ecm.identity.dto.response;

/**
 * The identity side of the admin dashboard: customers registered so far, and those who joined today and this month.
 */
public record CustomerSummaryResponse(long totalCustomers, long newToday, long newThisMonth) {
}
