ALTER TABLE orders ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE orders ADD COLUMN payment_method_id UUID;

-- One order per idempotency key. NULL-safe: multiple NULLs (orders created before this
-- column existed, or created by a flow that doesn't supply one) don't collide.
CREATE UNIQUE INDEX uk_orders_idempotency_key ON orders(idempotency_key) WHERE idempotency_key IS NOT NULL;

INSERT INTO shipping_methods (code, name, fee) VALUES
    ('STANDARD', 'Standard Delivery', 30000),
    ('EXPRESS', 'Express Delivery', 60000);
