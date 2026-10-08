-- A review is listed per product and attributed to the customer who wrote it. Neither can be
-- derived from order_item_id (it lives in order-service's database), so store them here.
ALTER TABLE product_reviews ADD COLUMN product_id  UUID NOT NULL REFERENCES products(id);
ALTER TABLE product_reviews ADD COLUMN customer_id UUID NOT NULL; -- ref -> Identity Service (accounts.id), no cross-DB FK

CREATE INDEX idx_product_reviews_product_status_created ON product_reviews (product_id, status, created_at DESC);
