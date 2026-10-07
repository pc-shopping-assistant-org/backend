-- Drop the VARIANT scope, per-variant targets and usage limits: the discount model only has ORDER, ALL_ITEMS and CATEGORY.
DROP TABLE discount_usages;
DROP TABLE discount_variants;
ALTER TABLE discounts DROP COLUMN usage_limit;

-- Discounts that no longer fit the model are kept for history but soft-deleted.
UPDATE discounts SET status = 'DELETED', application_scope = 'ALL_ITEMS' WHERE application_scope = 'VARIANT';
-- Expiry is derived from end_at at query time; DISABLED and EXPIRED collapse into INACTIVE (manually locked).
UPDATE discounts SET status = 'INACTIVE' WHERE status IN ('DISABLED', 'EXPIRED');

ALTER TABLE discounts DROP CONSTRAINT discounts_status_check;
ALTER TABLE discounts ADD CONSTRAINT discounts_status_check CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'));
ALTER TABLE discounts DROP CONSTRAINT discounts_application_scope_check;
ALTER TABLE discounts ADD CONSTRAINT discounts_application_scope_check CHECK (application_scope IN ('ORDER', 'ALL_ITEMS', 'CATEGORY'));

-- A code is unique only among non-deleted discounts, so a deleted discount's code can be reused.
ALTER TABLE discounts DROP CONSTRAINT discounts_code_key;
DROP INDEX discounts_normalized_code;
CREATE UNIQUE INDEX discounts_normalized_code ON discounts (upper(code)) WHERE code IS NOT NULL AND status <> 'DELETED';

ALTER TABLE discounts
    ALTER COLUMN start_at TYPE TIMESTAMPTZ,
    ALTER COLUMN end_at TYPE TIMESTAMPTZ,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

CREATE INDEX idx_discount_categories_category_id ON discount_categories(category_id);

-- Targets are now categories only, and only the discount touched by the changed row is checked.
CREATE OR REPLACE FUNCTION enforce_discount_scope_targets()
RETURNS TRIGGER AS $$
DECLARE
    target_discount_id  UUID;
    invalid_discount_id UUID;
BEGIN
    IF TG_TABLE_NAME = 'discounts' THEN
        target_discount_id := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN
        target_discount_id := OLD.discount_id;
    ELSE
        target_discount_id := NEW.discount_id;
    END IF;

    SELECT d.id
    INTO invalid_discount_id
    FROM discounts d
    WHERE d.id = target_discount_id
      AND (
            (
                d.application_scope IN ('ORDER', 'ALL_ITEMS')
                AND EXISTS (SELECT 1 FROM discount_categories dc WHERE dc.discount_id = d.id)
            )
            OR (
                d.application_scope = 'CATEGORY'
                AND NOT EXISTS (SELECT 1 FROM discount_categories dc WHERE dc.discount_id = d.id)
            )
      );

    IF invalid_discount_id IS NOT NULL THEN
        RAISE EXCEPTION 'Discount % has targets inconsistent with application_scope', invalid_discount_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
