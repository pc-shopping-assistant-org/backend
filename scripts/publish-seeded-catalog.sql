-- One-time publication of the 1,625 zero-stock variants in catalog_seed.json.
-- Guard the seed owner and dataset size; reruns leave already published rows alone.
BEGIN;
DO $publish$
DECLARE
    seed_owner CONSTANT uuid := '018f0000-0000-7000-8000-000000000001';
    dataset_size CONSTANT integer := 1625;
    matched_count integer;
    inactive_count integer;
    hidden_product_count integer;
    unexpected_status_count integer;
    published_count integer;
BEGIN
    SELECT count(*),
           count(*) FILTER (WHERE v.status = 'INACTIVE' AND v.quantity = 0),
           count(*) FILTER (WHERE p.status <> 'ACTIVE'),
           count(*) FILTER (WHERE v.status NOT IN ('ACTIVE', 'INACTIVE')
                           OR (v.status = 'INACTIVE' AND v.quantity <> 0))
      INTO matched_count, inactive_count, hidden_product_count, unexpected_status_count
      FROM product_variants v
      JOIN products p ON p.id = v.product_id
     WHERE v.created_by = seed_owner AND p.created_by = seed_owner;

    IF matched_count <> dataset_size OR hidden_product_count <> 0 OR unexpected_status_count <> 0
       OR inactive_count NOT IN (0, dataset_size) THEN
        RAISE EXCEPTION 'Seed publication precondition failed: total=%, inactive_zero_stock=%, hidden_products=%, unexpected_status=%',
            matched_count, inactive_count, hidden_product_count, unexpected_status_count;
    END IF;

    UPDATE product_variants v
       SET status = 'ACTIVE', updated_at = current_timestamp, updated_by = seed_owner
      FROM products p
     WHERE p.id = v.product_id
       AND p.created_by = seed_owner
       AND v.created_by = seed_owner
       AND v.status = 'INACTIVE'
       AND v.quantity = 0;
    GET DIAGNOSTICS published_count = ROW_COUNT;
    RAISE NOTICE 'Published % zero-stock seed variants', published_count;
END
$publish$;
COMMIT;
