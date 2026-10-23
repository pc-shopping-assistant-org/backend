-- A category template becomes one ordered list of attributes: the groups table only added a heading level.
ALTER TABLE category_attributes ADD COLUMN category_id UUID REFERENCES categories(id);

UPDATE category_attributes ca
SET category_id = g.category_id
FROM category_attribute_groups g
WHERE g.id = ca.category_group_id;

-- An attribute can be listed once per category; keep the first when old groups repeated it
DELETE FROM category_attributes ca
USING (
    SELECT ca2.id,
           ROW_NUMBER() OVER (PARTITION BY ca2.category_id, ca2.attribute_id ORDER BY g.display_order, ca2.display_order, ca2.id) AS rn
    FROM category_attributes ca2
    JOIN category_attribute_groups g ON g.id = ca2.category_group_id
) dup
WHERE ca.id = dup.id AND dup.rn > 1;

-- Keep the order people saw: by group, then by position inside the group
UPDATE category_attributes ca
SET display_order = ranked.position
FROM (
    SELECT ca2.id,
           ROW_NUMBER() OVER (PARTITION BY ca2.category_id ORDER BY g.display_order, g.name, ca2.display_order, ca2.id) - 1 AS position
    FROM category_attributes ca2
    JOIN category_attribute_groups g ON g.id = ca2.category_group_id
) ranked
WHERE ca.id = ranked.id;

ALTER TABLE category_attributes DROP COLUMN category_group_id;
ALTER TABLE category_attributes ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE category_attributes ADD CONSTRAINT uq_category_attributes_category_attribute UNIQUE (category_id, attribute_id);
CREATE INDEX idx_category_attributes_category_id ON category_attributes(category_id, display_order);

DROP TABLE category_attribute_groups;
