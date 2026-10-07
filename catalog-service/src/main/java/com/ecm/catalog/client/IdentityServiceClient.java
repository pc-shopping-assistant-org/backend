package com.ecm.catalog.client;

import com.ecm.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves reviewer names for the public review list; sent without a token because the list itself is public.
 */
@FeignClient(name = "identity-service")
public interface IdentityServiceClient {

    @GetMapping("/profile/customer-names")
    ApiResponse<Map<UUID, String>> getCustomerNames(@RequestParam("ids") List<UUID> ids);
}
