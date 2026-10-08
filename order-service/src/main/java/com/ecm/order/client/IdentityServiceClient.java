package com.ecm.order.client;

import com.ecm.common.response.ApiResponse;
import com.ecm.order.dto.response.AddressResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "identity-service")
public interface IdentityServiceClient {

    /** The saved addresses of the customer whose token is sent, so an address of someone else can never be picked. */
    @GetMapping("/users/addresses")
    ApiResponse<List<AddressResponse>> getMyAddresses(@RequestHeader("Authorization") String authorization);
}
