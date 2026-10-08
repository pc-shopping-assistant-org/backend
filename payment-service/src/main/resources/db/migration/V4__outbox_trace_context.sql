-- Trace context (W3C traceparent) captured when the row is written, so the relay can keep
-- the original request's trace when it publishes the message.
ALTER TABLE outbox_events ADD COLUMN trace_context VARCHAR(100);
