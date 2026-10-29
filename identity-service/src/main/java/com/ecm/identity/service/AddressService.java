package com.ecm.identity.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.identity.dto.request.CreateAddressRequest;
import com.ecm.identity.dto.request.UpdateAddressRequest;
import com.ecm.identity.dto.response.AddressResponse;
import com.ecm.identity.entity.CustomerAddress;
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.mapper.AddressMapper;
import com.ecm.identity.repository.CustomerAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** The delivery addresses of a customer. Every write goes through {@link #ownedAddress}, so one customer cannot touch another's. */
@Service
@RequiredArgsConstructor
public class AddressService {

    private final CustomerAddressRepository addressRepository;
    private final AddressMapper addressMapper;

    @Transactional(readOnly = true)
    public List<AddressResponse> list(UUID customerId) {
        return addressRepository.findByCustomerIdOrderByIsDefaultDesc(customerId).stream()
                .map(addressMapper::toResponse)
                .toList();
    }

    @Transactional
    public AddressResponse create(UUID customerId, CreateAddressRequest request) {
        // 1. The first address of a customer becomes the default, so checkout always has one to preselect
        boolean first = addressRepository.countByCustomerId(customerId) == 0;

        // 2. Store it with the text fields trimmed
        CustomerAddress address = CustomerAddress.builder()
                .customerId(customerId)
                .recipientName(request.recipientName().trim())
                .phone(request.phone().trim())
                .addressLine(request.addressLine().trim())
                .isDefault(first)
                .build();
        return addressMapper.toResponse(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse update(UUID customerId, UUID addressId, UpdateAddressRequest request) {
        // 1. Only the owner may edit the address
        CustomerAddress address = ownedAddress(customerId, addressId);

        // 2. Apply the trimmed values; the default flag is changed only through setDefault
        address.setRecipientName(request.recipientName().trim());
        address.setPhone(request.phone().trim());
        address.setAddressLine(request.addressLine().trim());
        return addressMapper.toResponse(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse setDefault(UUID customerId, UUID addressId) {
        // 1. Only the owner may pick the default
        CustomerAddress address = ownedAddress(customerId, addressId);

        // 2. Clear the current default first so a customer never has two
        addressRepository.clearDefault(customerId);

        // 3. Mark the chosen address as the default
        address.setDefault(true);
        return addressMapper.toResponse(addressRepository.save(address));
    }

    @Transactional
    public void delete(UUID customerId, UUID addressId) {
        CustomerAddress address = ownedAddress(customerId, addressId);
        addressRepository.delete(address);
    }

    /** Unknown and someone else's address give the same error, so a caller cannot probe which ids exist. */
    private CustomerAddress ownedAddress(UUID customerId, UUID addressId) {
        return addressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ADDRESS_NOT_OWNED));
    }
}
