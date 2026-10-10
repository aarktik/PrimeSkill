-- Proposed forward-only PostgreSQL change for an existing reviews table.
-- This file is not executed by Spring SQL initialization or Flyway as currently configured.
-- Confirm the migration baseline and staging procedure with Role E before applying it.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM reviews
        WHERE comment IS NOT NULL AND char_length(comment) > 2000
    ) THEN
        RAISE EXCEPTION 'Cannot limit reviews.comment to 2000 characters: oversized data exists';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_reviews_comment_length'
          AND conrelid = 'reviews'::regclass
    ) THEN
        ALTER TABLE reviews
            ADD CONSTRAINT ck_reviews_comment_length
            CHECK (comment IS NULL OR char_length(comment) <= 2000)
            NOT VALID;
    END IF;
END $$;

ALTER TABLE reviews
    VALIDATE CONSTRAINT ck_reviews_comment_length;
