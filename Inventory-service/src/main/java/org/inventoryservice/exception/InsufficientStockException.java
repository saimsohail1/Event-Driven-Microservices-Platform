package org.inventoryservice.exception;

/**
 * Permanent failure: stock cannot cover the order. Retrying will not change the
 * outcome, so the event is routed straight to the dead-letter topic where it is
 * visible for follow-up instead of silently disappearing into a log line.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productId, int available, int requested) {
        super("Insufficient stock for product " + productId + ": available " + available + ", requested " + requested);
    }
}
