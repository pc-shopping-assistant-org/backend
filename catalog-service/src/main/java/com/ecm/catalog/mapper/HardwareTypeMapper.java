package com.ecm.catalog.mapper;

import com.ecm.catalog.dto.response.HardwareTypeResponse;
import com.ecm.catalog.entity.HardwareTypeEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface HardwareTypeMapper {
    HardwareTypeResponse toResponse(HardwareTypeEntity hardwareType);

    List<HardwareTypeResponse> toResponseList(List<HardwareTypeEntity> hardwareTypes);
}
