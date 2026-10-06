-- Align identity tables with the new per-service schema (all-services-schema.sql).
-- All data was cleared before this migration, so these are structural-only changes.

-- Identity tables no longer track updated_at (see schema-migration-changes.md).
ALTER TABLE accounts DROP COLUMN updated_at;
ALTER TABLE customers DROP COLUMN updated_at;
ALTER TABLE customer_addresses DROP COLUMN updated_at;

-- Employee salary and join date were dropped (UC-ADM-EMP-004/005 capture neither).
ALTER TABLE employees DROP COLUMN updated_at;
ALTER TABLE employees DROP COLUMN salary;
ALTER TABLE employees DROP COLUMN joined_at;

-- ROLE_ADMIN profiles move from employees into their own table.
CREATE TABLE admins (
    account_id      UUID PRIMARY KEY REFERENCES accounts(id),
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    avatar_file_id  UUID, -- ref -> Media Service (files.id), no cross-DB FK
    address         VARCHAR(255),
    gender          VARCHAR(10) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    birthday        DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
