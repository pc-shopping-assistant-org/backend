package com.ecm.order.repository;

import com.ecm.order.entity.ShippingMethod;
import com.ecm.order.entity.ShippingMethodStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShippingMethodRepository extends JpaRepository<ShippingMethod, UUID> {

    List<ShippingMethod> findByStatusOrderByFeeAscNameAsc(ShippingMethodStatus status);
}
