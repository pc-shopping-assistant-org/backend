-- Outbox: reliable dispatch of Kafka events that must be written atomically with a
-- payment status change in the same DB transaction.
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    channel         VARCHAR(10) NOT NULL CHECK (channel IN ('KAFKA', 'RABBITMQ')),
    destination     VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    published_at    TIMESTAMP
);
CREATE INDEX idx_outbox_events_status ON outbox_events(status);
