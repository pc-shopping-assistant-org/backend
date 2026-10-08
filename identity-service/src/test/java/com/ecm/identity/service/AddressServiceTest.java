package com.ecm.identity.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.identity.dto.request.CreateAddressRequest;
import com.ecm.identity.dto.response.AddressResponse;
import com.ecm.identity.entity.CustomerAddress;
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.mapper.AddressMapper;
import com.ecm.identity.mapper.AddressMapperImpl;
import com.ecm.identity.repository.CustomerAddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddressServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID ADDRESS_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private CustomerAddressRepository addressRepository;
    private AddressService addressService;

    @BeforeEach
    void setUp() {
        addressRepository = mock(CustomerAddressRepository.class);
        addressService = new AddressService(addressRepository, new AddressMapperImpl());
    }

    @Test
    void firstAddressBecomesDefault() {
        when(addressRepository.countByCustomerId(CUSTOMER_ID)).thenReturn(0L);
        when(addressRepository.save(any(CustomerAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response = addressService.create(CUSTOMER_ID,
                new CreateAddressRequest("Nguyen Van A", "0900000001", "12 Le Loi, HCMC"));

        assertTrue(response.isDefault());
    }

    @Test
    void laterAddressesAreNotDefault() {
        when(addressRepository.countByCustomerId(CUSTOMER_ID)).thenReturn(1L);
        when(addressRepository.save(any(CustomerAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response = addressService.create(CUSTOMER_ID,
                new CreateAddressRequest("Nguyen Van A", "0900000001", "12 Le Loi, HCMC"));

        assertFalse(response.isDefault());
    }

    @Test
    void setDefaultClearsPreviousDefaultAndMarksTarget() {
        CustomerAddress address = CustomerAddress.builder()
                .id(ADDRESS_ID).customerId(CUSTOMER_ID)
                .recipientName("Nguyen Van A").phone("0900000001").addressLine("12 Le Loi").isDefault(false).build();
        when(addressRepository.findByIdAndCustomerId(ADDRESS_ID, CUSTOMER_ID)).thenReturn(Optional.of(address));
        when(addressRepository.save(any(CustomerAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response = addressService.setDefault(CUSTOMER_ID, ADDRESS_ID);

        verify(addressRepository).clearDefault(CUSTOMER_ID);
        assertTrue(response.isDefault());
    }

    @Test
    void deleteRejectsAddressOwnedByAnotherCustomer() {
        when(addressRepository.findByIdAndCustomerId(ADDRESS_ID, CUSTOMER_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> addressService.delete(CUSTOMER_ID, ADDRESS_ID));

        assertEquals(IdentityErrorCode.ADDRESS_NOT_OWNED, ex.getErrorCode());
    }

    @Test
    void listMapsAllAddresses() {
        CustomerAddress address = CustomerAddress.builder()
                .id(ADDRESS_ID).customerId(CUSTOMER_ID)
                .recipientName("Nguyen Van A").phone("0900000001").addressLine("12 Le Loi").isDefault(true).build();
        when(addressRepository.findByCustomerIdOrderByIsDefaultDesc(CUSTOMER_ID)).thenReturn(List.of(address));

        List<AddressResponse> result = addressService.list(CUSTOMER_ID);

        assertEquals(1, result.size());
        assertEquals("Nguyen Van A", result.get(0).recipientName());
    }
}
