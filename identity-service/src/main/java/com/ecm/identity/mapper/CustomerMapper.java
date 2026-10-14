package com.ecm.identity.mapper;

import com.ecm.identity.dto.request.RegisterRequest;
import com.ecm.identity.dto.response.UserSummaryResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.Customer;
import com.ecm.identity.entity.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    @Mapping(target = "accountId", ignore = true)
    Customer toEntity(RegisterRequest request);

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "role", source = "role.name")
    @Mapping(target = "avatarUrl", ignore = true)
    UserSummaryResponse toSummary(Account account, Role role, Customer customer);
}
