package org.inventoryservice.exception;

/**
 * Permanent failure: the product has no inventory record, so retrying the same
 * event cannot succeed.
 */
public class UnknownProductException extends RuntimeException {

    public UnknownProductException(String productId) {
        super("No inventory record for product " + productId);
    }
}
