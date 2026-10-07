CREATE TABLE orders (
    id          UUID            NOT NULL,
    product_id  VARCHAR(255)    NOT NULL,
    quantity    INTEGER         NOT NULL,
    price       NUMERIC(19, 2)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

CREATE INDEX idx_orders_created_at ON orders (created_at);

CREATE TABLE outbox_events (
    id             UUID         NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    topic          VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at   TIMESTAMP WITH TIME ZONE,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000),
    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

-- Supports the publisher's "oldest unpublished first" claim query.
CREATE INDEX idx_outbox_events_pending ON outbox_events (published_at, created_at);
