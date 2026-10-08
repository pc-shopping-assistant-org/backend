package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.order.dto.response.OrderDashboardResponse;
import com.ecm.order.dto.response.RevenuePointResponse;
import com.ecm.order.dto.response.TopVariantResponse;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.entity.StatisticsGranularity;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatisticsServiceTest {

    // 2026-03-31 18:30 UTC is already 01:30 on 1 April in Ho Chi Minh City (UTC+7)
    private static final Instant NOW = Instant.parse("2026-03-31T18:30:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;

    private OrderStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new OrderStatisticsService(orderRepository, orderItemRepository, Clock.fixed(NOW, ZoneOffset.UTC), ZONE);
    }

    private static Instant shopMidnight(String date) {
        return LocalDate.parse(date).atStartOfDay(ZONE).toInstant();
    }

    private OrderRepository.RevenueRow row(String period, long revenue, long orders) {
        return new OrderRepository.RevenueRow() {
            public LocalDate getPeriod() { return LocalDate.parse(period); }
            public long getRevenue() { return revenue; }
            public long getOrders() { return orders; }
        };
    }

    private OrderRepository.StatusCount count(OrderStatus status, long total) {
        return new OrderRepository.StatusCount() {
            public OrderStatus getStatus() { return status; }
            public long getTotal() { return total; }
        };
    }

    @Test
    void dashboardCutsTodayAndTheMonthByTheShopsCalendarAndFillsEveryStatus() {
        // The shop's date is 1 April, so the month starts on 1 April, not on the UTC date 31 March
        when(orderRepository.sumRevenue(shopMidnight("2026-04-01"), shopMidnight("2026-04-02"))).thenReturn(300L);
        when(orderRepository.countByStatus(any(), any())).thenReturn(List.of(count(OrderStatus.COMPLETED, 4), count(OrderStatus.CANCELLED, 1)));

        OrderDashboardResponse dashboard = service.getDashboard();

        assertThat(dashboard.revenueToday()).isEqualTo(300L);
        assertThat(dashboard.revenueThisMonth()).isEqualTo(300L);
        assertThat(dashboard.ordersByStatus()).hasSize(OrderStatus.values().length)
                .containsEntry(OrderStatus.COMPLETED, 4L).containsEntry(OrderStatus.CANCELLED, 1L).containsEntry(OrderStatus.SHIPPING, 0L);
    }

    @Test
    void dailyRevenueHasOnePointPerDayWithZerosForDaysWithoutASale() {
        when(orderRepository.sumRevenueByPeriod("day", "Asia/Ho_Chi_Minh", shopMidnight("2026-03-01"), shopMidnight("2026-03-04")))
                .thenReturn(List.of(row("2026-03-02", 500, 2)));

        List<RevenuePointResponse> series = service.getRevenue(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-03"), StatisticsGranularity.DAY);

        assertThat(series).containsExactly(
                new RevenuePointResponse(LocalDate.parse("2026-03-01"), 0, 0),
                new RevenuePointResponse(LocalDate.parse("2026-03-02"), 500, 2),
                new RevenuePointResponse(LocalDate.parse("2026-03-03"), 0, 0));
    }

    @Test
    void monthlyRevenueCoversWholeMonthsFromTheFirstToTheLastOne() {
        when(orderRepository.sumRevenueByPeriod("month", "Asia/Ho_Chi_Minh", shopMidnight("2026-01-01"), shopMidnight("2026-04-01")))
                .thenReturn(List.of(row("2026-02-01", 900, 3)));

        List<RevenuePointResponse> series = service.getRevenue(LocalDate.parse("2026-01-15"), LocalDate.parse("2026-03-10"), StatisticsGranularity.MONTH);

        assertThat(series).extracting(RevenuePointResponse::period)
                .containsExactly(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-01"), LocalDate.parse("2026-03-01"));
        assertThat(series).extracting(RevenuePointResponse::revenue).containsExactly(0L, 900L, 0L);
    }

    @Test
    void revenueRejectsAnInvertedOrOversizedRange() {
        LocalDate day = LocalDate.parse("2026-03-01");
        assertThatThrownBy(() -> service.getRevenue(day, day.minusDays(1), StatisticsGranularity.DAY)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getRevenue(day, day.plusDays(366), StatisticsGranularity.DAY)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getRevenue(day, day.plusMonths(60), StatisticsGranularity.MONTH)).isInstanceOf(BusinessException.class);
        verify(orderRepository, never()).sumRevenueByPeriod(any(), any(), any(), any());
    }

    @Test
    void aFullYearOfDaysIsAllowed() {
        LocalDate first = LocalDate.parse("2026-01-01");
        when(orderRepository.sumRevenueByPeriod(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(service.getRevenue(first, first.plusDays(365), StatisticsGranularity.DAY)).hasSize(366);
    }

    @Test
    void topVariantsDefaultToTenOfAllTimeAndTheRangeIncludesTheLastDay() {
        TopVariantResponse best = new TopVariantResponse(UUID.randomUUID(), "GPU", "SKU-1", "8GB", 7, 7000);
        when(orderItemRepository.findTopVariants(eq(Instant.EPOCH), any(), any())).thenReturn(List.of(best));
        assertThat(service.getTopVariants(null, null, null)).containsExactly(best);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(orderItemRepository).findTopVariants(eq(Instant.EPOCH), any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(10);

        service.getTopVariants(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"), 3);
        verify(orderItemRepository).findTopVariants(eq(shopMidnight("2026-03-01")), eq(shopMidnight("2026-04-01")), any());
    }

    @Test
    void topVariantsRejectABadLimitOrRange() {
        assertThatThrownBy(() -> service.getTopVariants(null, null, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getTopVariants(null, null, 51)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getTopVariants(LocalDate.parse("2026-03-02"), LocalDate.parse("2026-03-01"), 5))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void orderStatusCountsFilterByCreationDayAndFillMissingStatuses() {
        when(orderRepository.countByStatus(shopMidnight("2026-03-01"), shopMidnight("2026-03-08")))
                .thenReturn(List.of(count(OrderStatus.PENDING_CONFIRMATION, 2)));

        Map<OrderStatus, Long> counts = service.getOrderStatusCounts(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-07"));

        assertThat(counts).containsEntry(OrderStatus.PENDING_CONFIRMATION, 2L).containsEntry(OrderStatus.COMPLETED, 0L);
        assertThatThrownBy(() -> service.getOrderStatusCounts(LocalDate.parse("2026-03-02"), LocalDate.parse("2026-03-01")))
                .isInstanceOf(BusinessException.class);
    }
}
