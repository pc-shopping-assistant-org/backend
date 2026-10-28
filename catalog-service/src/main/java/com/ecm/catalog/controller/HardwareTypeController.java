package com.ecm.catalog.controller;

import com.ecm.catalog.dto.response.HardwareTypeResponse;
import com.ecm.catalog.service.HardwareTypeService;
import com.ecm.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/hardware-types")
@RequiredArgsConstructor
public class HardwareTypeController {
    private final HardwareTypeService service;

    @GetMapping
    public ApiResponse<List<HardwareTypeResponse>> getActiveTypes() {
        return ApiResponse.success(service.getActiveTypes());
    }
}
