-- Payments carry the customer of their order, so a customer reads only their own and the shop can search by customer.
-- Payments created before this column existed have no known customer (it lives in the Order Service database);
-- they get the nil UUID, which matches no account, until someone repairs them from the orders.
ALTER TABLE payments ADD COLUMN customer_id UUID;
UPDATE payments SET customer_id = '00000000-0000-0000-0000-000000000000';
ALTER TABLE payments ALTER COLUMN customer_id SET NOT NULL;

ALTER TABLE payment_methods DROP COLUMN updated_at;
ALTER TABLE payment_methods ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE payments
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ,
    ALTER COLUMN paid_at TYPE TIMESTAMPTZ;

-- CANCELLED: a pending payment whose order was cancelled. REFUNDED: a paid payment returned to the customer by hand.
ALTER TABLE payments DROP CONSTRAINT payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED', 'REFUNDED'));
ALTER TABLE payments DROP CONSTRAINT payments_check;
ALTER TABLE payments ADD CONSTRAINT payments_paid_at_check CHECK (status NOT IN ('PAID', 'REFUNDED') OR paid_at IS NOT NULL);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_customer_id ON payments(customer_id);
-- The transaction search of the shop: by period, newest first.
CREATE INDEX idx_payments_created_at ON payments(created_at DESC, id DESC);

-- Dedup for the Kafka events this service now consumes (order.cancelled).
CREATE TABLE inbox_events (
    event_id        UUID PRIMARY KEY,
    consumer_name   VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
