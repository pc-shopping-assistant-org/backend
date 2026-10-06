-- Align brand/category/attribute tables with the new per-service schema (all-services-schema.sql).
-- These tables held no data when this migration was written, so the changes are structural only.

-- brands: no updated_at; created_at is TIMESTAMPTZ; name/seo_name are unique only among non-deleted rows.
ALTER TABLE brands DROP COLUMN updated_at;
ALTER TABLE brands ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE brands DROP CONSTRAINT brands_name_key;
ALTER TABLE brands DROP CONSTRAINT brands_seo_name_key;
CREATE UNIQUE INDEX uk_brands_name ON brands(name) WHERE status <> 'DELETED';
CREATE UNIQUE INDEX uk_brands_seo_name ON brands(seo_name) WHERE status <> 'DELETED';

-- categories: add description (UC-ADM-CAT-003), same timestamp and soft-delete uniqueness changes as brands.
ALTER TABLE categories DROP COLUMN updated_at;
ALTER TABLE categories ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE categories ADD COLUMN description TEXT;
ALTER TABLE categories DROP CONSTRAINT categories_name_key;
ALTER TABLE categories DROP CONSTRAINT categories_seo_name_key;
CREATE UNIQUE INDEX uk_categories_name ON categories(name) WHERE status <> 'DELETED';
CREATE UNIQUE INDEX uk_categories_seo_name ON categories(seo_name) WHERE status <> 'DELETED';

-- Category attribute template tables have no status: rows are removed outright, not soft-deleted.
ALTER TABLE attribute_definitions DROP COLUMN status;
ALTER TABLE attribute_definitions ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE category_attribute_groups DROP COLUMN status;
ALTER TABLE category_attribute_groups ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE category_attributes DROP COLUMN status;
ALTER TABLE category_attributes ALTER COLUMN created_at TYPE TIMESTAMPTZ;
