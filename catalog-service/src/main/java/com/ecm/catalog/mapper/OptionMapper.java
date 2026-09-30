package com.ecm.catalog.mapper;

import com.ecm.catalog.dto.response.OptionResponse;
import com.ecm.catalog.entity.Option;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OptionMapper {

    OptionResponse toResponse(Option option);

    List<OptionResponse> toResponseList(List<Option> options);
}
