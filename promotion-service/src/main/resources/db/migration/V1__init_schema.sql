CREATE TABLE discounts (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    code                VARCHAR(50) UNIQUE,
    title               VARCHAR(255) NOT NULL,
    discount_type       VARCHAR(10) NOT NULL CHECK (discount_type IN ('PERCENT', 'FIXED')),
    value               INT NOT NULL,
    application_scope   VARCHAR(20) NOT NULL CHECK (application_scope IN ('ORDER', 'ALL_ITEMS', 'CATEGORY', 'VARIANT')),
    min_order_amount    BIGINT NOT NULL DEFAULT 0 CHECK (min_order_amount >= 0),
    start_at            TIMESTAMP NOT NULL,
    end_at              TIMESTAMP NOT NULL,
    description         TEXT,
    created_by          UUID, -- ref -> Identity Service (employees.account_id), no cross-DB FK
    updated_by          UUID, -- ref -> Identity Service (employees.account_id), no cross-DB FK
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE', 'INACTIVE', 'EXPIRED', 'DISABLED', 'DELETED')),
    CHECK (start_at < end_at),
    CHECK (
        (discount_type = 'PERCENT' AND value BETWEEN 1 AND 100)
        OR (discount_type = 'FIXED' AND value > 0)
    )
);

CREATE TABLE discount_categories (
    discount_id UUID NOT NULL REFERENCES discounts(id),
    category_id UUID NOT NULL, -- ref -> Catalog Service (categories.id), no cross-DB FK
    PRIMARY KEY (discount_id, category_id)
);

CREATE TABLE discount_variants (
    discount_id UUID NOT NULL REFERENCES discounts(id),
    variant_id  UUID NOT NULL, -- ref -> Catalog Service (product_variants.id), no cross-DB FK
    PRIMARY KEY (discount_id, variant_id)
);

-- Business rule: application_scope determines which target table a discount may
-- use. ORDER/ALL_ITEMS must have no targets at all; CATEGORY must have only
-- category targets; VARIANT must have only variant targets. Expressed as
-- deferred constraint triggers so the discount row and its targets can be
-- written atomically in one transaction, and re-checked if either side changes.
CREATE OR REPLACE FUNCTION enforce_discount_scope_targets()
RETURNS TRIGGER AS $$
DECLARE
    invalid_discount_id UUID;
BEGIN
    SELECT d.id
    INTO invalid_discount_id
    FROM discounts d
    WHERE
        (
            d.application_scope IN ('ORDER', 'ALL_ITEMS')
            AND (
                EXISTS (SELECT 1 FROM discount_categories dc WHERE dc.discount_id = d.id)
                OR EXISTS (SELECT 1 FROM discount_variants dv WHERE dv.discount_id = d.id)
            )
        )
        OR (
            d.application_scope = 'CATEGORY'
            AND (
                NOT EXISTS (SELECT 1 FROM discount_categories dc WHERE dc.discount_id = d.id)
                OR EXISTS (SELECT 1 FROM discount_variants dv WHERE dv.discount_id = d.id)
            )
        )
        OR (
            d.application_scope = 'VARIANT'
            AND (
                EXISTS (SELECT 1 FROM discount_categories dc WHERE dc.discount_id = d.id)
                OR NOT EXISTS (SELECT 1 FROM discount_variants dv WHERE dv.discount_id = d.id)
            )
        )
    LIMIT 1;

    IF invalid_discount_id IS NOT NULL THEN
        RAISE EXCEPTION 'Discount % has targets inconsistent with application_scope', invalid_discount_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_discount_scope_targets
    AFTER INSERT OR UPDATE ON discounts
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION enforce_discount_scope_targets();

CREATE CONSTRAINT TRIGGER trg_discount_category_targets
    AFTER INSERT OR UPDATE OR DELETE ON discount_categories
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION enforce_discount_scope_targets();

CREATE CONSTRAINT TRIGGER trg_discount_variant_targets
    AFTER INSERT OR UPDATE OR DELETE ON discount_variants
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION enforce_discount_scope_targets();
