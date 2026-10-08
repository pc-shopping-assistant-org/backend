package com.ecm.identity.dto.response;

import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.entity.Gender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CustomerDetailResponse(
        UUID accountId,
        String email,
        String phone,
        AccountStatus status,
        String firstName,
        String lastName,
        Gender gender,
        LocalDate birthday,
        UUID avatarFileId,
        String avatarUrl,
        Instant createdAt,
        List<AddressResponse> addresses) {

    public static CustomerDetailResponse of(CustomerResponse customer, List<AddressResponse> addresses) {
        return new CustomerDetailResponse(customer.accountId(), customer.email(), customer.phone(), customer.status(),
                customer.firstName(), customer.lastName(), customer.gender(), customer.birthday(),
                customer.avatarFileId(), customer.avatarUrl(), customer.createdAt(), addresses);
    }
}
