package com.ecm.order.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.order.client.CatalogServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Throwaway endpoint used to verify OpenTelemetry/Zipkin trace propagation end to end. */
@RestController
@RequestMapping("/trace-test")
@RequiredArgsConstructor
public class TraceTestController {

    private final CatalogServiceClient catalogServiceClient;

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        ApiResponse<String> catalogResponse = catalogServiceClient.ping();
        return ApiResponse.success("Order trace-test ping", "order-service -> " + catalogResponse.getData());
    }
}
