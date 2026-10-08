package com.ecm.order.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.order.dto.response.OrderDashboardResponse;
import com.ecm.order.dto.response.RevenuePointResponse;
import com.ecm.order.dto.response.TopVariantResponse;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.entity.StatisticsGranularity;
import com.ecm.order.service.OrderStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Restricted to ROLE_ADMIN by SecurityConfig.
@RestController
@RequestMapping("/orders/admin")
@RequiredArgsConstructor
public class OrderStatisticsController {

    private final OrderStatisticsService statisticsService;

    @GetMapping("/dashboard")
    public ApiResponse<OrderDashboardResponse> getDashboard() {
        return ApiResponse.success(statisticsService.getDashboard());
    }

    @GetMapping("/statistics/revenue")
    public ApiResponse<List<RevenuePointResponse>> getRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") StatisticsGranularity granularity) {
        return ApiResponse.success(statisticsService.getRevenue(from, to, granularity));
    }

    @GetMapping("/statistics/top-variants")
    public ApiResponse<List<TopVariantResponse>> getTopVariants(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(statisticsService.getTopVariants(from, to, limit));
    }

    @GetMapping("/statistics/order-status")
    public ApiResponse<Map<OrderStatus, Long>> getOrderStatusCounts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(statisticsService.getOrderStatusCounts(from, to));
    }
}
