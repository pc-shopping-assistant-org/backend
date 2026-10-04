-- Add update timestamps to category and brand master data.
ALTER TABLE categories ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
ALTER TABLE brands ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
