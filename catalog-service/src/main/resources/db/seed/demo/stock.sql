-- Explicit demo data reset. Re-running sets eligible SKU stock to exactly 10.
-- The caller owns the transaction; normal seed-prod never runs this resource.
UPDATE brands SET status = 'ACTIVE' WHERE status = 'INACTIVE';
UPDATE categories SET status = 'ACTIVE' WHERE status = 'INACTIVE';

UPDATE products p
   SET status = 'ACTIVE', updated_at = current_timestamp
 WHERE p.status = 'INACTIVE'
   AND EXISTS (SELECT 1 FROM categories c WHERE c.id = p.category_id AND c.status = 'ACTIVE')
   AND (p.brand_id IS NULL OR EXISTS (SELECT 1 FROM brands b WHERE b.id = p.brand_id AND b.status = 'ACTIVE'));

UPDATE product_variants v
   SET quantity = 10, status = 'ACTIVE', updated_at = current_timestamp
 WHERE v.status <> 'DELETED'
   AND (v.quantity <> 10 OR v.status <> 'ACTIVE')
   AND EXISTS (
       SELECT 1 FROM products p JOIN categories c ON c.id = p.category_id
        WHERE p.id = v.product_id AND p.status = 'ACTIVE' AND c.status = 'ACTIVE'
          AND (p.brand_id IS NULL OR EXISTS (SELECT 1 FROM brands b WHERE b.id = p.brand_id AND b.status = 'ACTIVE'))
   );
