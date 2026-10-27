-- Storefront/admin category ordering is explicit and independent of the name.
ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS display_order INT NOT NULL DEFAULT 0;

ALTER TABLE categories
    ADD CONSTRAINT ck_categories_display_order CHECK (display_order >= 0);

CREATE INDEX IF NOT EXISTS idx_categories_parent_display_order
    ON categories(parent_id, display_order, name);

-- Preserve the current component navigation order when the column is introduced.
UPDATE categories
SET display_order = CASE seo_name
    WHEN 'cpu' THEN 10
    WHEN 'mainboard' THEN 20
    WHEN 'ram' THEN 30
    WHEN 'gpu' THEN 40
    WHEN 'psu' THEN 50
    WHEN 'case' THEN 60
    WHEN 'cooler' THEN 70
    WHEN 'storage' THEN 80
    WHEN 'ssd' THEN 80
    WHEN 'hdd' THEN 80
    WHEN 'monitor' THEN 90
    WHEN 'mouse' THEN 100
    WHEN 'keyboard' THEN 110
    WHEN 'headset' THEN 120
    WHEN 'other' THEN 999
    ELSE display_order
END;
