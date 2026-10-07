CREATE TABLE inventory (
    product_id         VARCHAR(255) NOT NULL,
    available_quantity INTEGER      NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_inventory PRIMARY KEY (product_id),
    CONSTRAINT ck_inventory_quantity_non_negative CHECK (available_quantity >= 0)
);

-- Idempotency ledger: one row per order whose stock effect has been applied,
-- so a redelivered event can never decrement the same stock twice.
CREATE TABLE processed_orders (
    order_id     UUID NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_processed_orders PRIMARY KEY (order_id)
);
