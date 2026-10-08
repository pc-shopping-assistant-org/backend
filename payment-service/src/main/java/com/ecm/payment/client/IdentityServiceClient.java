package com.ecm.payment.client;

import com.ecm.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/** Payments hold only the account id of a customer, so a search by customer name has to ask the Identity Service who they are. */
@FeignClient(name = "identity-service")
public interface IdentityServiceClient {

    @GetMapping("/profile/admin/customer-ids")
    ApiResponse<List<UUID>> findCustomerIds(@RequestParam("name") String name, @RequestHeader("Authorization") String bearerToken);
}
