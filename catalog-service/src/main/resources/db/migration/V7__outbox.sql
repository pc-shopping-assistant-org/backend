-- Reliable dispatch of the replies of the stock saga, written in the same transaction as the variants.quantity change.
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    channel         VARCHAR(10) NOT NULL CHECK (channel IN ('KAFKA', 'RABBITMQ')),
    destination     VARCHAR(255) NOT NULL, -- Kafka topic or RabbitMQ routing key
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    trace_context   VARCHAR(100), -- W3C traceparent captured at write time
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);
-- The dispatcher only ever polls PENDING rows oldest-first; published rows stay out of the index.
CREATE INDEX idx_outbox_events_pending ON outbox_events(created_at) WHERE status = 'PENDING';
