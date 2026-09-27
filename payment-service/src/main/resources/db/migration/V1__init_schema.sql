CREATE TABLE payment_methods (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    code        VARCHAR(50) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP
);

CREATE TABLE payments (
    id                          UUID PRIMARY KEY DEFAULT uuidv7(),
    order_id                    UUID NOT NULL, -- ref -> Order Service (orders.id), no cross-DB FK
    payment_method_id           UUID NOT NULL REFERENCES payment_methods(id),
    amount                      BIGINT NOT NULL CHECK (amount >= 0),
    paid_at                     TIMESTAMP,
    provider_transaction_code   VARCHAR(100) UNIQUE,
    created_at                  TIMESTAMP NOT NULL DEFAULT now(),
    created_by                  UUID, -- ref -> Identity Service (employees.account_id), no cross-DB FK
    updated_at                  TIMESTAMP,
    updated_by                  UUID, -- ref -> Identity Service (employees.account_id), no cross-DB FK
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PAID', 'FAILED')),
    CHECK (status <> 'PAID' OR paid_at IS NOT NULL)
);
CREATE INDEX idx_payments_payment_method_id ON payments(payment_method_id);
-- An order can be paid only once: unique among PAID payments, but retries/failed
-- attempts for the same order are allowed to co-exist as separate rows.
CREATE UNIQUE INDEX uk_payments_order_paid ON payments(order_id) WHERE status = 'PAID';
