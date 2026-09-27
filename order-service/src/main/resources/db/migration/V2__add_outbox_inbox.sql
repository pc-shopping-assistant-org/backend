-- Outbox: reliable dispatch of messages (Kafka events or RabbitMQ commands) that must
-- be written atomically with a business change in the same DB transaction.
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    channel         VARCHAR(10) NOT NULL CHECK (channel IN ('KAFKA', 'RABBITMQ')),
    destination     VARCHAR(255) NOT NULL, -- Kafka topic or RabbitMQ routing key
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    published_at    TIMESTAMP
);
CREATE INDEX idx_outbox_events_status ON outbox_events(status);

-- Inbox: dedup for at-least-once Kafka/RabbitMQ delivery. event_id is the producer's
-- message id, so re-delivery of the same event is a no-op instead of double-processing.
CREATE TABLE inbox_events (
    event_id        UUID PRIMARY KEY,
    consumer_name   VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMP NOT NULL DEFAULT now()
);
