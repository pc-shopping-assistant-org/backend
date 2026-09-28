ALTER TABLE payments ADD COLUMN idempotency_key VARCHAR(100);

-- Protects against the saga's create-payment call being retried (Feign timeout, event
-- redelivery) and inserting a second PENDING row for the exact same trigger. Distinct
-- from uk_payments_order_paid: a customer is still allowed multiple genuine attempts
-- (retry after a FAILED payment) for the same order — those get different keys.
CREATE UNIQUE INDEX uk_payments_idempotency_key ON payments(idempotency_key) WHERE idempotency_key IS NOT NULL;

INSERT INTO payment_methods (code, name) VALUES
    ('COD', 'Cash on Delivery'),
    ('ONLINE_CARD', 'Online Card Payment');
