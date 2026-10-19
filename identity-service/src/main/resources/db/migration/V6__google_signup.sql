-- A customer who signed up with Google has no password yet and may not have given a phone number.
-- Phone stays UNIQUE: Postgres lets many NULLs coexist, so only real numbers are compared.
ALTER TABLE accounts ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE accounts ALTER COLUMN password_hash DROP NOT NULL;
