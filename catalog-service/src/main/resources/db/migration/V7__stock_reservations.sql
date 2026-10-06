-- One row per order whose stock is currently reserved (taken off product_variants.quantity).
-- It makes reserving and releasing idempotent and lets a release do nothing for an order that holds no stock.
CREATE TABLE stock_reservations (
    order_id    UUID PRIMARY KEY, -- ref -> Order Service (orders.id), no cross-DB FK
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
