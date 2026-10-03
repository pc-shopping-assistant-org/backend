ALTER TABLE discounts ADD COLUMN usage_limit BIGINT CHECK (usage_limit > 0);
CREATE UNIQUE INDEX discounts_normalized_code ON discounts (upper(code)) WHERE code IS NOT NULL;
CREATE TABLE discount_usages (
    discount_id UUID NOT NULL REFERENCES discounts(id),
    checkout_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (discount_id, checkout_key)
);
