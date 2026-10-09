package com.ecm.identity.mapper;

import com.ecm.identity.dto.request.CreateEmployeeRequest;
import com.ecm.identity.dto.request.UpdateEmployeeRequest;
import com.ecm.identity.dto.response.UserSummaryResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.Employee;
import com.ecm.identity.entity.Role;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface EmployeeMapper {

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "role", source = "role.name")
    @Mapping(target = "avatarUrl", ignore = true)
    UserSummaryResponse toSummary(Account account, Role role, Employee employee);

    @Mapping(target = "accountId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Employee toEntity(CreateEmployeeRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "accountId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(@MappingTarget Employee employee, UpdateEmployeeRequest request);
}
