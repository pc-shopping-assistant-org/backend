package com.ecm.order.service;

import com.ecm.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Makes invoice numbers: INV- plus 10 random characters, never 0, O, 1, I or L, which are easy to misread. */
@Component
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private static final String PREFIX = "INV-";
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int RANDOM_LENGTH = 10;
    private static final int MAX_ATTEMPTS = 5;

    private final OrderRepository orderRepository;
    private final SecureRandom random = new SecureRandom();

    /** Draws again when the number is taken; the unique index on invoice_number is the final guard. */
    public String next() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String invoiceNumber = random();
            if (!orderRepository.existsByInvoiceNumber(invoiceNumber)) {
                return invoiceNumber;
            }
        }
        throw new IllegalStateException("Could not generate an unused invoice number in " + MAX_ATTEMPTS + " attempts");
    }

    String random() {
        StringBuilder invoiceNumber = new StringBuilder(PREFIX);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            invoiceNumber.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return invoiceNumber.toString();
    }
}
