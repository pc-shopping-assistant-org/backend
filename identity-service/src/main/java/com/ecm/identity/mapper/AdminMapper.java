package com.ecm.identity.mapper;

import com.ecm.identity.dto.response.UserSummaryResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.Admin;
import com.ecm.identity.entity.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdminMapper {

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "role", source = "role.name")
    @Mapping(target = "avatarUrl", ignore = true)
    UserSummaryResponse toSummary(Account account, Role role, Admin admin);
}
