-- Reference data only. Match existing names before inserting; preserve IDs,
-- visibility, hierarchy and administrator edits when the command is rerun.
WITH hardware_types (code, name_vi, name_en, display_order) AS (
    VALUES
        ('CPU', 'CPU', 'CPU', 10),
        ('MAINBOARD', 'Mainboard', 'Mainboard', 20),
        ('RAM', 'RAM', 'RAM', 30),
        ('GPU', 'Card đồ họa', 'Graphics card', 40),
        ('PSU', 'Nguồn máy tính', 'Power supply', 50),
        ('CASE', 'Vỏ case', 'PC case', 60),
        ('COOLER', 'Tản nhiệt', 'CPU cooler', 70),
        ('STORAGE', 'Ổ cứng', 'Storage', 80),
        ('MONITOR', 'Màn hình', 'Monitor', 90),
        ('MOUSE', 'Chuột', 'Mouse', 100),
        ('KEYBOARD', 'Bàn phím', 'Keyboard', 110),
        ('HEADSET', 'Tai nghe', 'Headset', 120),
        ('PREBUILT_PC', 'Máy tính để bàn', 'Prebuilt PC', 130),
        ('PC_GAMING', 'PC Gaming', 'Gaming PC', 140),
        ('PC_WORKSTATION', 'PC Workstation', 'Workstation PC', 150),
        ('PC_OFFICE', 'PC Văn phòng', 'Office PC', 160),
        ('PC_AI', 'PC AI', 'AI PC', 170),
        ('PC_MINI', 'PC Mini', 'Mini PC', 180),
        ('OTHER', 'Khác', 'Other', 999)
)
INSERT INTO hardware_types (code, name_vi, name_en, display_order)
SELECT code, name_vi, name_en, display_order FROM hardware_types
ON CONFLICT (code) DO NOTHING;

WITH hardware_categories (hardware_type_code, name, seo_name, display_order) AS (
    VALUES
        ('CPU', 'CPU', 'cpu', 10),
        ('MAINBOARD', 'Mainboard', 'mainboard', 20),
        ('RAM', 'RAM', 'ram', 30),
        ('GPU', 'Card đồ họa', 'gpu', 40),
        ('PSU', 'Nguồn máy tính', 'psu', 50),
        ('CASE', 'Vỏ case', 'case', 60),
        ('COOLER', 'Tản nhiệt', 'cooler', 70),
        ('STORAGE', 'Ổ cứng', 'storage', 80),
        ('MONITOR', 'Màn hình', 'monitor', 90),
        ('MOUSE', 'Chuột', 'mouse', 100),
        ('KEYBOARD', 'Bàn phím', 'keyboard', 110),
        ('HEADSET', 'Tai nghe', 'headset', 120),
        ('OTHER', 'Khác', 'other', 999)
)
INSERT INTO categories (name, seo_name, hardware_type_id, display_order)
SELECT seed.name, COALESCE(existing.seo_name, seed.seo_name), type.id, seed.display_order
FROM hardware_categories seed
LEFT JOIN hardware_types type ON type.code = seed.hardware_type_code
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

-- Repair legacy rows created before hardware types became mandatory.
UPDATE categories
SET hardware_type_id = (SELECT id FROM hardware_types WHERE code = 'OTHER')
WHERE hardware_type_id IS NULL
  AND status <> 'DELETED';
