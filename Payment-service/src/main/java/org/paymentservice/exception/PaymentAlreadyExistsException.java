package org.paymentservice.exception;

import java.util.UUID;

public class PaymentAlreadyExistsException extends RuntimeException {

    public PaymentAlreadyExistsException(UUID orderId) {
        super("A payment already exists for order " + orderId);
    }
}
