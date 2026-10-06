package com.ecm.payment.repository;

import com.ecm.payment.entity.PaymentMethod;
import com.ecm.payment.entity.PaymentMethodStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    List<PaymentMethod> findByStatusOrderByNameAsc(PaymentMethodStatus status);
}
