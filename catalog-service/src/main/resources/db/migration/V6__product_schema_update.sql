-- Align product tables with the new per-service schema (all-services-schema.sql).
-- These tables held no data when this migration was written, so the changes are structural only.

-- Suppliers are gone: no use case manages them and products no longer reference them.
DROP TABLE product_suppliers;
DROP TABLE suppliers;

-- products: TIMESTAMPTZ, created_by required, seo_name unique only among non-deleted rows.
ALTER TABLE products ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE products ALTER COLUMN updated_at TYPE TIMESTAMPTZ;
ALTER TABLE products ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE products DROP CONSTRAINT products_seo_name_key;
CREATE UNIQUE INDEX uk_products_seo_name ON products(seo_name) WHERE status <> 'DELETED';

-- product_variants: list_price -> price, optional own image, sku/barcode unique only among non-deleted rows.
ALTER TABLE product_variants RENAME COLUMN list_price TO price;
ALTER TABLE product_variants RENAME CONSTRAINT product_variants_list_price_check TO product_variants_price_check;
ALTER TABLE product_variants ADD COLUMN image_file_id UUID; -- ref -> Media Service (files.id), no cross-DB FK
ALTER TABLE product_variants ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE product_variants ALTER COLUMN updated_at TYPE TIMESTAMPTZ;
ALTER TABLE product_variants DROP CONSTRAINT product_variants_sku_key;
ALTER TABLE product_variants DROP CONSTRAINT product_variants_barcode_key;
CREATE UNIQUE INDEX uk_product_variants_sku ON product_variants(sku) WHERE status <> 'DELETED';
CREATE UNIQUE INDEX uk_product_variants_barcode ON product_variants(barcode) WHERE status <> 'DELETED';

-- Images now belong to the product (gallery), not to a variant.
DROP TABLE product_images;
CREATE TABLE product_images (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    product_id          UUID NOT NULL REFERENCES products(id),
    image_file_id       UUID NOT NULL, -- ref -> Media Service (files.id), no cross-DB FK
    is_main             BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (product_id, image_file_id)
);
CREATE UNIQUE INDEX uk_product_images_main ON product_images(product_id) WHERE is_main = true;

-- Options are (name, value) pairs shared between variants; "type" and "status" are gone.
DROP TABLE variant_options;
DROP TABLE options;
CREATE TABLE options (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    name        VARCHAR(100) NOT NULL,
    value       VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (name, value)
);

CREATE TABLE variant_options (
    product_variant_id  UUID NOT NULL REFERENCES product_variants(id),
    option_id           UUID NOT NULL REFERENCES options(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (product_variant_id, option_id)
);
CREATE INDEX idx_variant_options_option_id ON variant_options(option_id);

-- A variant can have at most one option of each normalized (trim + uppercase) options.name.
CREATE OR REPLACE FUNCTION check_variant_option_type_uniqueness()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM variant_options vo
        JOIN options o ON o.id = vo.option_id
        WHERE vo.product_variant_id = NEW.product_variant_id
        GROUP BY upper(trim(o.name))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'A product variant cannot have multiple options with the same name';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_variant_options_unique_type
    AFTER INSERT OR UPDATE ON variant_options
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_variant_option_type_uniqueness();

-- Renaming an option can make two options of one variant collide; re-check the variants using it.
CREATE OR REPLACE FUNCTION check_option_rename_uniqueness()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM variant_options vo
        JOIN options o ON o.id = vo.option_id
        WHERE vo.product_variant_id IN (
            SELECT product_variant_id FROM variant_options WHERE option_id = NEW.id
        )
        GROUP BY vo.product_variant_id, upper(trim(o.name))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'A product variant cannot have multiple options with the same name';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_options_type_unique_after_change
    AFTER UPDATE OF name ON options
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_option_rename_uniqueness();
