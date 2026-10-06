package com.ecm.order.service;

import com.ecm.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceNumberGeneratorTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final InvoiceNumberGenerator generator = new InvoiceNumberGenerator(orderRepository);

    @Test
    void numbersHaveThePrefixAndTenCharactersWithoutConfusingOnes() {
        for (int i = 0; i < 200; i++) {
            assertTrue(generator.random().matches("INV-[2-9A-HJKMN-Z]{10}"));
        }
    }

    @Test
    void aTakenNumberIsDrawnAgain() {
        when(orderRepository.existsByInvoiceNumber(anyString())).thenReturn(true, false);

        generator.next();

        verify(orderRepository, times(2)).existsByInvoiceNumber(anyString());
    }

    @Test
    void givesUpAfterFiveTakenNumbers() {
        when(orderRepository.existsByInvoiceNumber(anyString())).thenReturn(true);

        assertThrows(IllegalStateException.class, generator::next);
        verify(orderRepository, times(5)).existsByInvoiceNumber(anyString());
        assertEquals(14, generator.random().length());
    }
}
