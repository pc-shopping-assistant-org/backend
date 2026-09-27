CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    name        VARCHAR(50) NOT NULL UNIQUE,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
);

CREATE TABLE accounts (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    phone           VARCHAR(15) NOT NULL UNIQUE,
    google_subject  VARCHAR(255),
    password_hash   VARCHAR(255) NOT NULL,
    role_id         UUID NOT NULL REFERENCES roles(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED', 'DELETED')),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP
);
CREATE INDEX idx_accounts_role_id ON accounts(role_id);
-- A verified Google provider subject can link to at most one account; local
-- accounts that have not linked Google keep this field NULL.
CREATE UNIQUE INDEX uk_accounts_google_subject ON accounts(google_subject) WHERE google_subject IS NOT NULL;

CREATE TABLE employees (
    account_id      UUID PRIMARY KEY REFERENCES accounts(id),
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    avatar_file_id  UUID,
    address         VARCHAR(255),
    gender          VARCHAR(10) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    salary          BIGINT NOT NULL DEFAULT 0 CHECK (salary >= 0),
    birthday        DATE,
    joined_at       DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP
);

CREATE TABLE customers (
    account_id      UUID PRIMARY KEY REFERENCES accounts(id),
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    avatar_file_id  UUID,
    gender          VARCHAR(10) CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    birthday        DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP
);

CREATE TABLE customer_addresses (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    customer_id     UUID NOT NULL REFERENCES customers(account_id),
    recipient_name  VARCHAR(100) NOT NULL,
    phone           VARCHAR(15) NOT NULL,
    address_line    VARCHAR(500) NOT NULL,
    is_default      BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP
);
CREATE INDEX idx_customer_addresses_customer_id ON customer_addresses(customer_id);
-- Only one default address per customer, not a single default across the whole table.
CREATE UNIQUE INDEX uk_customer_addresses_default ON customer_addresses(customer_id) WHERE is_default = true;
