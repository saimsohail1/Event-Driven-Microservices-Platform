package org.inventoryservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Marks an order whose stock effect has already been applied. Written in the
 * same transaction as the stock change, which makes the consumer idempotent.
 */
@Entity
@Table(name = "processed_orders")
public class ProcessedOrder {

    @Id
    private UUID orderId;

    @Column(nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedOrder() {
        // for JPA
    }

    public ProcessedOrder(UUID orderId) {
        this.orderId = orderId;
        this.processedAt = Instant.now();
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
