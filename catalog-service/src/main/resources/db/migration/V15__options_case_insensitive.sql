-- Options are looked up ignoring case, so their uniqueness must ignore case too; otherwise two concurrent saves of
-- "Color: Grey" and "color: grey" both insert and every later lookup finds two rows.

-- 1. Fold options that differ only by case into the oldest one
WITH ranked AS (
    SELECT id, first_value(id) OVER (PARTITION BY lower(name), lower(value) ORDER BY created_at, id) AS keep_id
    FROM options
)
UPDATE variant_options vo SET option_id = r.keep_id
FROM ranked r
WHERE vo.option_id = r.id AND r.id <> r.keep_id;

DELETE FROM options o
USING (SELECT id, first_value(id) OVER (PARTITION BY lower(name), lower(value) ORDER BY created_at, id) AS keep_id
       FROM options) r
WHERE o.id = r.id AND r.id <> r.keep_id;

-- 2. Replace the case-sensitive rule with a case-insensitive one (the target of INSERT ... ON CONFLICT)
ALTER TABLE options DROP CONSTRAINT options_name_value_key;
CREATE UNIQUE INDEX uk_options_name_value_ci ON options (lower(name), lower(value));
