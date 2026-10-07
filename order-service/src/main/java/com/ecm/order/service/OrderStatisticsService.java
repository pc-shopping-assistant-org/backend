package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.order.dto.response.OrderDashboardResponse;
import com.ecm.order.dto.response.RevenuePointResponse;
import com.ecm.order.dto.response.TopVariantResponse;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.entity.StatisticsGranularity;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the shop's owner reads off the orders: the dashboard (UC-ADM-DASH-001) and the statistics (UC-ADM-STAT-001).
 * Revenue counts only completed orders, by the day they were delivered, in the shop's time zone.
 */
@Service
@RequiredArgsConstructor
public class OrderStatisticsService {

    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final int MAX_TOP_LIMIT = 50;
    private static final long MAX_DAILY_PERIODS = 366;
    private static final long MAX_MONTHLY_PERIODS = 60;
    private static final Instant NO_UPPER_BOUND = Instant.parse("9999-12-31T00:00:00Z");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final Clock clock;
    private final ZoneId zone;

    @Transactional(readOnly = true)
    public OrderDashboardResponse getDashboard() {
        // 1. Today and this month, as the shop's calendar sees them
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate firstOfMonth = today.withDayOfMonth(1);

        // 2. Revenue of both periods, and every order by status
        long revenueToday = orderRepository.sumRevenue(startOf(today), startOf(today.plusDays(1)));
        long revenueThisMonth = orderRepository.sumRevenue(startOf(firstOfMonth), startOf(today.plusDays(1)));
        return new OrderDashboardResponse(revenueToday, revenueThisMonth, countByStatus(Instant.EPOCH, NO_UPPER_BOUND));
    }

    /** One point per day or month between the two dates, inclusive, zero where nothing was delivered. */
    @Transactional(readOnly = true)
    public List<RevenuePointResponse> getRevenue(LocalDate from, LocalDate to, StatisticsGranularity granularity) {
        // 1. Whole days, or whole months, and a range that is not larger than a chart can use
        boolean monthly = granularity == StatisticsGranularity.MONTH;
        LocalDate first = monthly ? from.withDayOfMonth(1) : from;
        LocalDate last = monthly ? to.with(TemporalAdjusters.firstDayOfMonth()) : to;
        long periods = (monthly ? ChronoUnit.MONTHS : ChronoUnit.DAYS).between(first, last) + 1;
        if (last.isBefore(first) || periods > (monthly ? MAX_MONTHLY_PERIODS : MAX_DAILY_PERIODS)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }

        // 2. One grouped query for the whole range
        LocalDate end = monthly ? last.plusMonths(1) : last.plusDays(1);
        Map<LocalDate, RevenuePointResponse> sold = new LinkedHashMap<>();
        orderRepository.sumRevenueByPeriod(granularity.name().toLowerCase(), zone.getId(), startOf(first), startOf(end))
                .forEach(row -> sold.put(row.getPeriod(), new RevenuePointResponse(row.getPeriod(), row.getRevenue(), row.getOrders())));

        // 3. Fill the periods without a sale so the series has no holes
        List<RevenuePointResponse> series = new ArrayList<>();
        for (LocalDate period = first; period.isBefore(end); period = monthly ? period.plusMonths(1) : period.plusDays(1)) {
            series.add(sold.getOrDefault(period, new RevenuePointResponse(period, 0, 0)));
        }
        return series;
    }

    /** The best sellers of the period, by quantity; with no dates, of all time. */
    @Transactional(readOnly = true)
    public List<TopVariantResponse> getTopVariants(LocalDate from, LocalDate to, Integer limit) {
        int size = limit == null ? DEFAULT_TOP_LIMIT : limit;
        if (size < 1 || size > MAX_TOP_LIMIT || isEmptyRange(from, to)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        return orderItemRepository.findTopVariants(from == null ? Instant.EPOCH : startOf(from),
                to == null ? NO_UPPER_BOUND : startOf(to.plusDays(1)), PageRequest.of(0, size));
    }

    /** The orders created in the period, counted per status, with a zero for the statuses nobody has; with no dates, all of them. */
    @Transactional(readOnly = true)
    public Map<OrderStatus, Long> getOrderStatusCounts(LocalDate from, LocalDate to) {
        if (isEmptyRange(from, to)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        return countByStatus(from == null ? Instant.EPOCH : startOf(from), to == null ? NO_UPPER_BOUND : startOf(to.plusDays(1)));
    }

    private Map<OrderStatus, Long> countByStatus(Instant from, Instant to) {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, 0L);
        }
        orderRepository.countByStatus(from, to).forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return counts;
    }

    private boolean isEmptyRange(LocalDate from, LocalDate to) {
        return from != null && to != null && to.isBefore(from);
    }

    private Instant startOf(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }
}
