package com.ecm.identity.mapper;

import com.ecm.identity.dto.response.UserSummaryResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.Employee;
import com.ecm.identity.entity.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EmployeeMapper {

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "role", source = "role.name")
    UserSummaryResponse toSummary(Account account, Role role, Employee employee);
}
