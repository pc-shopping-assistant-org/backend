-- A cart no longer has a status or a guest session: every customer owns exactly one cart.
-- Guest carts and carts that were already converted or abandoned are dropped; only each customer's ACTIVE cart is kept.
DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE customer_id IS NULL OR status <> 'ACTIVE');
DELETE FROM carts WHERE customer_id IS NULL OR status <> 'ACTIVE';

DROP INDEX uk_carts_customer_active;
DROP INDEX uk_carts_session_active;
ALTER TABLE carts DROP CONSTRAINT carts_check;
ALTER TABLE carts DROP COLUMN session_token;
ALTER TABLE carts DROP COLUMN status;
ALTER TABLE carts DROP COLUMN updated_at;
ALTER TABLE carts ALTER COLUMN customer_id SET NOT NULL;
ALTER TABLE carts ADD CONSTRAINT carts_customer_id_key UNIQUE (customer_id);
ALTER TABLE carts ALTER COLUMN created_at TYPE TIMESTAMPTZ;
