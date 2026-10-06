package com.ecm.catalog.service;

import com.ecm.catalog.entity.ProductVariant;
import com.ecm.catalog.messaging.rabbitmq.command.ReleaseStockCommand;
import com.ecm.catalog.messaging.rabbitmq.command.ReserveStockCommand;
import com.ecm.catalog.messaging.rabbitmq.command.StockItem;
import com.ecm.catalog.repository.ProductVariantRepository;
import com.ecm.catalog.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockReservationServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID VARIANT_A = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID VARIANT_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private ProductVariantRepository variantRepository;
    private StockReservationRepository reservationRepository;
    private StockReservationService service;
    private ProductVariant a;
    private ProductVariant b;

    @BeforeEach
    void setUp() {
        variantRepository = mock(ProductVariantRepository.class);
        reservationRepository = mock(StockReservationRepository.class);
        service = new StockReservationService(variantRepository, reservationRepository);
        a = ProductVariant.builder().id(VARIANT_A).quantity(5).build();
        b = ProductVariant.builder().id(VARIANT_B).quantity(2).build();
        when(variantRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(a, b));
    }

    private static ReserveStockCommand reserve(StockItem... items) {
        return new ReserveStockCommand(UUID.randomUUID(), ORDER_ID, List.of(items));
    }

    private static ReleaseStockCommand release(StockItem... items) {
        return new ReleaseStockCommand(UUID.randomUUID(), ORDER_ID, List.of(items));
    }

    @Test
    void reservesEveryLineAndRecordsTheReservation() {
        Optional<String> failure = service.reserve(reserve(new StockItem(VARIANT_A, 3), new StockItem(VARIANT_B, 2)));

        assertTrue(failure.isEmpty());
        assertEquals(2, a.getQuantity());
        assertEquals(0, b.getQuantity());
        verify(reservationRepository).save(any());
    }

    @Test
    void reservesNothingWhenAnyLineIsShort() {
        Optional<String> failure = service.reserve(reserve(new StockItem(VARIANT_A, 3), new StockItem(VARIANT_B, 3)));

        assertTrue(failure.isPresent());
        assertEquals(5, a.getQuantity());
        assertEquals(2, b.getQuantity());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void aVariantThatDoesNotExistMakesTheReservationFail() {
        when(variantRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(a));

        assertTrue(service.reserve(reserve(new StockItem(VARIANT_A, 1), new StockItem(VARIANT_B, 1))).isPresent());
        assertEquals(5, a.getQuantity());
    }

    @Test
    void aRepeatedLineOfTheSameVariantIsCheckedAsOneTotal() {
        assertTrue(service.reserve(reserve(new StockItem(VARIANT_A, 3), new StockItem(VARIANT_A, 3))).isPresent());
        assertEquals(5, a.getQuantity());
    }

    @Test
    void anOrderThatAlreadyHoldsAReservationIsNotReservedTwice() {
        when(reservationRepository.existsById(ORDER_ID)).thenReturn(true);

        assertTrue(service.reserve(reserve(new StockItem(VARIANT_A, 3))).isEmpty());

        assertEquals(5, a.getQuantity());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void releaseGivesTheReservedStockBack() {
        when(reservationRepository.deleteByOrderIdReturningCount(ORDER_ID)).thenReturn(1);

        service.release(release(new StockItem(VARIANT_A, 3), new StockItem(VARIANT_B, 1)));

        assertEquals(8, a.getQuantity());
        assertEquals(3, b.getQuantity());
    }

    @Test
    void releaseForAnOrderThatReservedNothingChangesNothing() {
        when(reservationRepository.deleteByOrderIdReturningCount(ORDER_ID)).thenReturn(0);

        service.release(release(new StockItem(VARIANT_A, 3)));

        assertEquals(5, a.getQuantity());
        verify(variantRepository, never()).findAllByIdForUpdate(anyCollection());
    }
}
