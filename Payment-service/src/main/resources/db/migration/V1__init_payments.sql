CREATE TABLE payments (
    id         UUID           NOT NULL,
    order_id   UUID           NOT NULL,
    amount     NUMERIC(19, 2) NOT NULL,
    status     VARCHAR(20)    NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    -- One payment per order. This is the hard guarantee behind the consumer's
    -- idempotency check, so a redelivered event cannot double-charge.
    CONSTRAINT uq_payments_order_id UNIQUE (order_id)
);

CREATE INDEX idx_payments_created_at ON payments (created_at);
