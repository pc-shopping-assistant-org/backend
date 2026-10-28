-- SSD and HDD are hardware types of their own, so their categories get a name in both languages like the other types.
INSERT INTO hardware_types (code, name_vi, name_en, display_order)
VALUES
    ('SSD', 'Ổ cứng SSD', 'SSD', 81),
    ('HDD', 'Ổ cứng HDD', 'HDD', 82)
ON CONFLICT (code) DO NOTHING;

-- The categories already seeded under STORAGE move to their own type; fresh databases get it from the seed.
UPDATE categories c
SET hardware_type_id = h.id
FROM hardware_types h, hardware_types storage
WHERE storage.code = 'STORAGE'
  AND c.hardware_type_id = storage.id
  AND ((h.code = 'SSD' AND c.name = 'Ổ cứng SSD') OR (h.code = 'HDD' AND c.name = 'Ổ cứng HDD'));
