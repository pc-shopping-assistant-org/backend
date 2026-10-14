package com.ecm.order.service;

import com.ecm.order.dto.response.ShippingMethodResponse;
import com.ecm.order.entity.ShippingMethodStatus;
import com.ecm.order.mapper.ShippingMethodMapper;
import com.ecm.order.repository.ShippingMethodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The shipping methods a customer can pick at checkout. */
@Service
@RequiredArgsConstructor
public class ShippingMethodService {

    private final ShippingMethodRepository shippingMethodRepository;
    private final ShippingMethodMapper shippingMethodMapper;

    @Transactional(readOnly = true)
    public List<ShippingMethodResponse> getActiveMethods() {
        return shippingMethodMapper.toResponses(shippingMethodRepository.findByStatusOrderByFeeAscNameAsc(ShippingMethodStatus.ACTIVE));
    }
}
