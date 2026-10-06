package com.ecm.identity.mapper;

import com.ecm.identity.dto.response.AddressResponse;
import com.ecm.identity.entity.CustomerAddress;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AddressMapper {

    @Mapping(source = "default", target = "isDefault")
    AddressResponse toResponse(CustomerAddress address);
}
