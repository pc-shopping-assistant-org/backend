-- Edit policy: a review can be edited once, within 30 days of creation. NULL edited_at = never edited.
ALTER TABLE product_reviews ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE product_reviews ADD COLUMN edited_at TIMESTAMPTZ;
ALTER TABLE product_reviews ADD CONSTRAINT product_reviews_edited_at_check
    CHECK (edited_at IS NULL OR (edited_at >= created_at AND edited_at <= created_at + INTERVAL '30 days'));
