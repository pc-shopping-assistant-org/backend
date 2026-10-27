package com.ecm.order.helper;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.order.client.IdentityServiceClient;
import com.ecm.order.dto.request.CreateOrderRequest;
import com.ecm.order.dto.response.AddressResponse;
import com.ecm.order.exception.OrderErrorCode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Who an order is delivered to: a saved address of the customer, kept in the Identity Service, or one typed in. */
@Component
@RequiredArgsConstructor
public class RecipientResolver {

    private static final String IDENTITY_SERVICE = "identity-service";

    private final IdentityServiceClient identityServiceClient;

    public record Recipient(String name, String phone, String address) {
    }

    /** A saved address of the customer, or the recipient typed in; never a mix, and never an address of someone else. */
    public Recipient resolve(CreateOrderRequest request, String bearerToken) {
        if (request.customerAddressId() != null) {
            if (request.recipientName() != null || request.recipientPhone() != null || request.deliveryAddress() != null) {
                throw new BusinessException(OrderErrorCode.INVALID_RECIPIENT);
            }
            AddressResponse address = fetchAddresses(bearerToken).stream()
                    .filter(saved -> saved.id().equals(request.customerAddressId())).findFirst()
                    .orElseThrow(() -> new BusinessException(OrderErrorCode.INVALID_RECIPIENT));
            return new Recipient(address.recipientName(), address.phone(), address.addressLine());
        }
        if (isBlank(request.recipientName()) || isBlank(request.recipientPhone()) || isBlank(request.deliveryAddress())) {
            throw new BusinessException(OrderErrorCode.INVALID_RECIPIENT);
        }
        return new Recipient(request.recipientName().trim(), request.recipientPhone().trim(), request.deliveryAddress().trim());
    }

    private List<AddressResponse> fetchAddresses(String bearerToken) {
        try {
            return Objects.requireNonNull(identityServiceClient.getMyAddresses(bearerToken).getData());
        } catch (FeignException ex) {
            throw new ExternalServiceException(IDENTITY_SERVICE, ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
