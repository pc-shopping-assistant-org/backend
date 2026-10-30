-- Stocking of every variant in the seed datasets (components, prebuilt PCs
-- and gear) that exists in this database: ACTIVE with quantity 100.
-- Overwrites the quantity of every seed variant; rows not created by seed_owner are untouched.
BEGIN;
DO $stock$
DECLARE
    seed_owner CONSTANT uuid := '018f0000-0000-7000-8000-000000000001';
    stock_quantity CONSTANT integer := 100;
    updated_products integer;
    updated_variants integer;
BEGIN
    UPDATE products
       SET status = 'ACTIVE', updated_at = current_timestamp, updated_by = seed_owner
     WHERE created_by = seed_owner
       AND status <> 'ACTIVE';
    GET DIAGNOSTICS updated_products = ROW_COUNT;

    UPDATE product_variants v
       SET status = 'ACTIVE', quantity = stock_quantity,
           updated_at = current_timestamp, updated_by = seed_owner
      FROM products p
     WHERE p.id = v.product_id
       AND p.created_by = seed_owner
       AND v.created_by = seed_owner
       AND (v.status <> 'ACTIVE' OR v.quantity <> stock_quantity);
    GET DIAGNOSTICS updated_variants = ROW_COUNT;

    RAISE NOTICE 'Activated % seed products, updated % seed variants to ACTIVE/%',
        updated_products, updated_variants, stock_quantity;
END
$stock$;
COMMIT;
