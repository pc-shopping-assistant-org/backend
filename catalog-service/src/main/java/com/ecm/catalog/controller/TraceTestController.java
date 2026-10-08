package com.ecm.catalog.controller;

import com.ecm.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Throwaway endpoint used to verify OpenTelemetry/Zipkin trace propagation end to end.
 */
@RestController
@RequestMapping("/trace-test")
public class TraceTestController {

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.success("pong from catalog-service");
    }
}
