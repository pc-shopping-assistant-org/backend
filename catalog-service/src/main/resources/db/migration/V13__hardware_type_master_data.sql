-- Hardware category codes are master data. Generic categories reference OTHER.
CREATE TABLE hardware_types (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    code          VARCHAR(30) NOT NULL UNIQUE,
    name_vi       VARCHAR(100) NOT NULL,
    name_en       VARCHAR(100) NOT NULL,
    status        VARCHAR(15) NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    display_order INT NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO hardware_types (code, name_vi, name_en, display_order)
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
ON CONFLICT (code) DO NOTHING;

ALTER TABLE categories
    ADD COLUMN hardware_type_id UUID;

UPDATE categories c
SET hardware_type_id = h.id
FROM hardware_types h
WHERE h.code = c.hardware_type;

-- Legacy rows without a taxonomy are now explicitly classified as OTHER.
UPDATE categories
SET hardware_type_id = (SELECT id FROM hardware_types WHERE code = 'OTHER')
WHERE hardware_type_id IS NULL;

ALTER TABLE categories
    DROP CONSTRAINT ck_categories_hardware_type;

ALTER TABLE categories
    DROP COLUMN hardware_type;

ALTER TABLE categories
    ADD CONSTRAINT fk_categories_hardware_type
    FOREIGN KEY (hardware_type_id) REFERENCES hardware_types(id);

ALTER TABLE categories
    ALTER COLUMN hardware_type_id SET NOT NULL;

CREATE INDEX idx_categories_hardware_type_id ON categories(hardware_type_id);
