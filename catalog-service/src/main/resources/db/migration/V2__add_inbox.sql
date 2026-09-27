-- Inbox: dedup for at-least-once RabbitMQ/Kafka delivery. event_id is the producer's
-- message id, so re-delivery of the same command/event is a no-op instead of double-processing.
CREATE TABLE inbox_events (
    event_id        UUID PRIMARY KEY,
    consumer_name   VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMP NOT NULL DEFAULT now()
);
