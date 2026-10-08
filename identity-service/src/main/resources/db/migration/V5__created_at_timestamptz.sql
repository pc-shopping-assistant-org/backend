-- Align created_at with all-services-schema.sql (TIMESTAMPTZ). Existing values are
-- interpreted in the database session time zone.
ALTER TABLE accounts           ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE employees          ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE admins             ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE customers          ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE customer_addresses ALTER COLUMN created_at TYPE TIMESTAMPTZ;
