-- Reference data only. Match existing names before inserting; preserve IDs,
-- visibility, hierarchy and administrator edits when the command is rerun.
WITH hardware_categories (hardware_type, name, seo_name) AS (
    VALUES
        ('CPU', 'CPU', 'cpu'),
        ('MAINBOARD', 'Mainboard', 'mainboard'),
        ('RAM', 'RAM', 'ram'),
        ('GPU', 'Card đồ họa', 'gpu'),
        ('PSU', 'Nguồn máy tính', 'psu'),
        ('CASE', 'Vỏ case', 'case'),
        ('COOLER', 'Tản nhiệt', 'cooler'),
        ('STORAGE', 'Ổ cứng', 'storage'),
        ('MONITOR', 'Màn hình', 'monitor'),
        ('MOUSE', 'Chuột', 'mouse'),
        ('KEYBOARD', 'Bàn phím', 'keyboard'),
        ('HEADSET', 'Tai nghe', 'headset')
)
INSERT INTO categories (name, seo_name, hardware_type)
SELECT seed.name, COALESCE(existing.seo_name, seed.seo_name), seed.hardware_type
FROM hardware_categories seed
LEFT JOIN categories existing
    ON (existing.seo_name = seed.seo_name OR lower(existing.name) = lower(seed.name))
       AND existing.status <> 'DELETED'
WHERE existing.id IS NOT NULL OR NOT EXISTS (
    SELECT 1 FROM categories retired
    WHERE retired.status = 'DELETED'
      AND (retired.seo_name = seed.seo_name OR lower(retired.name) = lower(seed.name))
)
ON CONFLICT (seo_name) WHERE status <> 'DELETED'
DO NOTHING;
