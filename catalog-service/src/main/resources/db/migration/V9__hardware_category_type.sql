ALTER TABLE categories ADD COLUMN hardware_type VARCHAR(20);
ALTER TABLE categories ADD CONSTRAINT ck_categories_hardware_type CHECK (
    hardware_type IN ('CPU', 'MAINBOARD', 'RAM', 'GPU', 'PSU', 'CASE', 'COOLER',
                     'STORAGE', 'MONITOR', 'MOUSE', 'KEYBOARD', 'HEADSET')
);
