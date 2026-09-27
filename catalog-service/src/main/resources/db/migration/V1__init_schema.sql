CREATE TABLE brands (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    name            VARCHAR(255) NOT NULL UNIQUE,
    seo_name        VARCHAR(255) NOT NULL UNIQUE,
    description     TEXT,
    image_file_id   UUID,
    status          VARCHAR(15) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    name        VARCHAR(255) NOT NULL UNIQUE,
    seo_name    VARCHAR(255) NOT NULL UNIQUE,
    parent_id   UUID REFERENCES categories(id),
    status      VARCHAR(15) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_categories_parent_id ON categories(parent_id);

CREATE TABLE attribute_definitions (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    key             VARCHAR(100) NOT NULL UNIQUE,
    display_name    VARCHAR(255) NOT NULL,
    data_type       VARCHAR(20) NOT NULL CHECK (data_type IN ('NUMBER', 'STRING', 'ENUM', 'BOOLEAN')),
    unit            VARCHAR(50),
    allowed_values  JSONB,
    aliases         JSONB,
    filterable      BOOLEAN NOT NULL DEFAULT false,
    comparable      BOOLEAN NOT NULL DEFAULT false,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE category_attribute_groups (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    category_id     UUID NOT NULL REFERENCES categories(id),
    name            VARCHAR(100) NOT NULL,
    display_order   INT NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_category_attribute_groups_category_id ON category_attribute_groups(category_id);

CREATE TABLE category_attributes (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    category_group_id  UUID NOT NULL REFERENCES category_attribute_groups(id),
    attribute_id        UUID NOT NULL REFERENCES attribute_definitions(id),
    required            BOOLEAN NOT NULL DEFAULT false,
    display_order       INT NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (category_group_id, attribute_id)
);

CREATE TABLE suppliers (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    name        VARCHAR(255) NOT NULL UNIQUE,
    email       VARCHAR(255) UNIQUE,
    phone       VARCHAR(15),
    address     VARCHAR(255),
    description TEXT,
    status      VARCHAR(15) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP
);

CREATE TABLE products (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    name            VARCHAR(255) NOT NULL,
    seo_name        VARCHAR(255) NOT NULL UNIQUE,
    brand_id        UUID REFERENCES brands(id),
    category_id     UUID NOT NULL REFERENCES categories(id),
    specifications  JSONB,
    description     TEXT,
    status          VARCHAR(15) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    created_by      UUID, -- ref -> Identity Service (employees.account_id), no cross-DB FK
    updated_at      TIMESTAMP,
    updated_by      UUID  -- ref -> Identity Service (employees.account_id), no cross-DB FK
);
CREATE INDEX idx_products_brand_id ON products(brand_id);
CREATE INDEX idx_products_category_id ON products(category_id);

CREATE TABLE product_variants (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    product_id      UUID NOT NULL REFERENCES products(id),
    list_price      BIGINT NOT NULL CHECK (list_price >= 0),
    quantity        INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    sku             VARCHAR(100) NOT NULL UNIQUE,
    model           VARCHAR(100),
    description     TEXT,
    warranty_months INT NOT NULL CHECK (warranty_months > 0),
    barcode         VARCHAR(100) UNIQUE,
    release_at      DATE,
    status          VARCHAR(15) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    created_by      UUID NOT NULL, -- ref -> Identity Service (employees.account_id), no cross-DB FK
    updated_at      TIMESTAMP,
    updated_by      UUID -- ref -> Identity Service (employees.account_id), no cross-DB FK
);
CREATE INDEX idx_product_variants_product_id ON product_variants(product_id);

CREATE TABLE product_images (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    name                VARCHAR(255),
    product_variant_id UUID NOT NULL REFERENCES product_variants(id),
    file_id             UUID NOT NULL, -- ref -> Media Service (files.id), no cross-DB FK
    is_main             BOOLEAN NOT NULL DEFAULT false,
    status              VARCHAR(15) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (product_variant_id, file_id)
);
-- Only one main image per variant among its ACTIVE images.
CREATE UNIQUE INDEX uk_product_images_main ON product_images(product_variant_id)
    WHERE is_main = true AND status = 'ACTIVE';

CREATE TABLE options (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    type        VARCHAR(50) NOT NULL,
    name        VARCHAR(100) NOT NULL UNIQUE,
    value       TEXT NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE variant_options (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    product_variant_id UUID NOT NULL REFERENCES product_variants(id),
    option_id           UUID NOT NULL REFERENCES options(id),
    status              VARCHAR(15) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (product_variant_id, option_id)
);

-- Business rule: a variant can have at most one non-deleted option of each
-- normalized (trim + uppercase) options.type. Expressed as a deferred
-- constraint trigger (not a plain UNIQUE) because it depends on joining
-- options.type and must also re-check when an option's own type/status
-- changes after the fact.
CREATE OR REPLACE FUNCTION check_variant_option_type_uniqueness()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM variant_options vo
        JOIN options o ON o.id = vo.option_id
        WHERE vo.status <> 'DELETED'
          AND o.status <> 'DELETED'
        GROUP BY vo.product_variant_id, upper(trim(o.type))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'A product variant cannot have multiple options of the same type';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_variant_options_unique_type
    AFTER INSERT OR UPDATE OR DELETE ON variant_options
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_variant_option_type_uniqueness();

CREATE CONSTRAINT TRIGGER trg_options_type_unique_after_change
    AFTER UPDATE ON options
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_variant_option_type_uniqueness();

CREATE TABLE product_suppliers (
    product_id  UUID NOT NULL REFERENCES products(id),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, supplier_id)
);

CREATE TABLE product_reviews (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    order_item_id   UUID NOT NULL UNIQUE, -- ref -> Order Service (order_items.id), no cross-DB FK
    rating          INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment         TEXT,
    status          VARCHAR(15) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
