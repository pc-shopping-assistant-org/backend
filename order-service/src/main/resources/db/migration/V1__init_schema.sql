CREATE TABLE carts (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    customer_id     UUID, -- ref -> Identity Service (customers.account_id), no cross-DB FK
    session_token   VARCHAR(255),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CONVERTED', 'ABANDONED', 'EXPIRED')),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP,
    -- Exactly one of customer_id (logged-in cart) or session_token (guest cart) is set.
    CHECK (
        (customer_id IS NOT NULL AND session_token IS NULL)
        OR (customer_id IS NULL AND session_token IS NOT NULL)
    )
);
-- Only one ACTIVE cart per customer / per guest session.
CREATE UNIQUE INDEX uk_carts_customer_active ON carts(customer_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uk_carts_session_active ON carts(session_token) WHERE status = 'ACTIVE';

CREATE TABLE cart_items (
    cart_id     UUID NOT NULL REFERENCES carts(id),
    variant_id  UUID NOT NULL, -- ref -> Catalog Service (product_variants.id), no cross-DB FK
    quantity    INT NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (cart_id, variant_id)
);

CREATE TABLE shipping_methods (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    code        VARCHAR(50) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    fee         BIGINT NOT NULL DEFAULT 0 CHECK (fee >= 0),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP
);

CREATE TABLE orders (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    customer_id         UUID, -- ref -> Identity Service (customers.account_id), no cross-DB FK
    order_discount_id   UUID, -- ref -> Promotion Service (discounts.id), no cross-DB FK
    shipping_method_id  UUID NOT NULL REFERENCES shipping_methods(id),
    subtotal_amount     BIGINT NOT NULL CHECK (subtotal_amount >= 0),
    discount_amount     BIGINT NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    shipping_fee        BIGINT NOT NULL CHECK (shipping_fee >= 0),
    total_amount        BIGINT NOT NULL CHECK (total_amount >= 0),
    order_time          TIMESTAMP NOT NULL DEFAULT now(),
    note                TEXT,
    delivery_address    VARCHAR(500) NOT NULL,
    recipient_name      VARCHAR(100) NOT NULL,
    recipient_phone     VARCHAR(15) NOT NULL,
    delivered_at        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    created_by          UUID, -- ref -> Identity Service (employees.account_id), no cross-DB FK
    updated_at          TIMESTAMP,
    updated_by          UUID, -- ref -> Identity Service (employees.account_id), no cross-DB FK
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING_CONFIRMATION'
                            CHECK (status IN (
                                'PENDING_PAYMENT', 'PENDING_CONFIRMATION', 'CONFIRMED',
                                'SHIPPING', 'COMPLETED', 'CANCELLED'
                            ))
);
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_shipping_method_id ON orders(shipping_method_id);

CREATE TABLE order_items (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    order_id            UUID NOT NULL REFERENCES orders(id),
    product_variant_id  UUID NOT NULL, -- ref -> Catalog Service (product_variants.id), no cross-DB FK
    quantity            INT NOT NULL CHECK (quantity > 0),
    unit_price           BIGINT NOT NULL CHECK (unit_price >= 0),
    item_discount_id     UUID, -- ref -> Promotion Service (discounts.id), no cross-DB FK
    item_discount        BIGINT NOT NULL DEFAULT 0 CHECK (item_discount >= 0),
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at           TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
