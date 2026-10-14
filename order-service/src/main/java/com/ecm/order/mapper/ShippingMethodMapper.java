package com.ecm.order.mapper;

import com.ecm.order.dto.response.ShippingMethodResponse;
import com.ecm.order.entity.ShippingMethod;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ShippingMethodMapper {

    ShippingMethodResponse toResponse(ShippingMethod method);

    List<ShippingMethodResponse> toResponses(List<ShippingMethod> methods);
}
