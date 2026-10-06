-- shipping_methods: no updated_at, timestamps with time zone
ALTER TABLE shipping_methods DROP COLUMN updated_at;
ALTER TABLE shipping_methods ALTER COLUMN created_at TYPE TIMESTAMPTZ;

-- Orders without a customer (guests are gone) or a payment method cannot satisfy the new NOT NULL columns.
DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE customer_id IS NULL OR payment_method_id IS NULL);
DELETE FROM orders WHERE customer_id IS NULL OR payment_method_id IS NULL;

-- Older orders get a made-up invoice number in the same INV-xxxxxxxxxx shape (0 and 1 never appear in the real alphabet).
UPDATE orders SET invoice_number = 'INV-' || translate(upper(substr(md5(id::text), 1, 10)), '01', 'XY') WHERE invoice_number IS NULL;
UPDATE orders SET delivered_at = coalesce(updated_at, created_at) WHERE status = 'COMPLETED' AND delivered_at IS NULL;

ALTER TABLE orders
    ALTER COLUMN customer_id SET NOT NULL,
    ALTER COLUMN payment_method_id SET NOT NULL,
    ALTER COLUMN invoice_number SET NOT NULL;
ALTER TABLE orders DROP COLUMN order_time;
ALTER TABLE orders
    ALTER COLUMN delivered_at TYPE TIMESTAMPTZ,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

DROP INDEX ux_orders_invoice_number;
CREATE UNIQUE INDEX ux_orders_invoice_number ON orders(invoice_number);

/*
 * subtotal_amount is now the sum of the lines after their own discounts, and discount_amount is the order voucher
 * only, so older rows may not fit these checks; NOT VALID enforces them on new and updated rows.
 */
ALTER TABLE orders ADD CONSTRAINT orders_completed_has_delivered_at CHECK (status <> 'COMPLETED' OR delivered_at IS NOT NULL);
ALTER TABLE orders ADD CONSTRAINT orders_discount_within_subtotal CHECK (discount_amount <= subtotal_amount) NOT VALID;
ALTER TABLE orders ADD CONSTRAINT orders_total_formula CHECK (total_amount = subtotal_amount - discount_amount + shipping_fee) NOT VALID;

-- Customer history newest first with keyset pagination; it also serves plain lookups by customer.
DROP INDEX idx_orders_customer_id;
CREATE INDEX idx_orders_customer_created ON orders(customer_id, created_at DESC, id DESC);
CREATE INDEX idx_orders_status_created ON orders(status, created_at DESC, id DESC);
CREATE INDEX idx_orders_completed_delivered ON orders(delivered_at DESC) WHERE status = 'COMPLETED';

-- order_items: the line is a snapshot of the variant at order time, and carries the discount of the whole line.
ALTER TABLE order_items RENAME COLUMN item_discount_id TO discount_id;
ALTER TABLE order_items RENAME COLUMN item_discount TO discount_amount;
ALTER TABLE order_items DROP COLUMN status;
ALTER TABLE order_items
    ADD COLUMN product_name  VARCHAR(255),
    ADD COLUMN sku           VARCHAR(100),
    ADD COLUMN variant_label VARCHAR(500);
UPDATE order_items SET product_name = 'Unknown product', sku = 'UNKNOWN' WHERE product_name IS NULL;
ALTER TABLE order_items
    ALTER COLUMN product_name SET NOT NULL,
    ALTER COLUMN sku SET NOT NULL,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE order_items ADD CONSTRAINT order_items_order_variant_key UNIQUE (order_id, product_variant_id);
ALTER TABLE order_items ADD CONSTRAINT order_items_discount_within_line CHECK (discount_amount <= quantity * unit_price) NOT VALID;
CREATE INDEX idx_order_items_product_variant_id ON order_items(product_variant_id);

-- Every status change of an order, written in the same transaction as the orders.status update.
-- The first row of an order has from_status NULL. Allowed transitions are enforced by the service.
CREATE TABLE order_status_history (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    order_id        UUID NOT NULL REFERENCES orders(id),
    from_status     VARCHAR(30) CHECK (from_status IN (
                        'PENDING_PAYMENT', 'PENDING_CONFIRMATION', 'CONFIRMED',
                        'SHIPPING', 'COMPLETED', 'CANCELLED'
                    )),
    to_status       VARCHAR(30) NOT NULL CHECK (to_status IN (
                        'PENDING_PAYMENT', 'PENDING_CONFIRMATION', 'CONFIRMED',
                        'SHIPPING', 'COMPLETED', 'CANCELLED'
                    )),
    changed_by      UUID, -- ref -> Identity Service (employees.account_id), no cross-DB FK; NULL = customer or system
    reason          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_status_history_order_created ON order_status_history(order_id, created_at);
INSERT INTO order_status_history (order_id, from_status, to_status, created_at) SELECT id, NULL, status, created_at FROM orders;

-- Outbox and inbox: timestamps with time zone, and the dispatcher only polls PENDING rows oldest-first.
ALTER TABLE outbox_events
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN published_at TYPE TIMESTAMPTZ;
DROP INDEX idx_outbox_events_status;
CREATE INDEX idx_outbox_events_pending ON outbox_events(created_at) WHERE status = 'PENDING';
ALTER TABLE inbox_events ALTER COLUMN processed_at TYPE TIMESTAMPTZ;
