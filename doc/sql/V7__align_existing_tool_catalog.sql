-- Primeskill / Role B: forward migration for an existing Sprint-0 PostgreSQL schema.
-- Proposed Flyway version: V7. Confirm the team's actual baseline/version order before
-- moving this file to code/src/main/resources/db/migration/. Back up and test on staging first.
-- This file is not discovered or executed from doc/sql.

DO $$
DECLARE
    oversized_value_exists BOOLEAN;
    website_url_exists BOOLEAN;
    repository_url_exists BOOLEAN;
BEGIN
    IF EXISTS (SELECT 1 FROM categories WHERE length(description) > 500) THEN
        RAISE EXCEPTION 'Cannot narrow categories.description to varchar(500): oversized data exists';
    END IF;
    IF EXISTS (SELECT 1 FROM tools WHERE length(name) > 150) THEN
        RAISE EXCEPTION 'Cannot narrow tools.name to varchar(150): oversized data exists';
    END IF;
    IF EXISTS (SELECT 1 FROM tools WHERE length(slug) > 170) THEN
        RAISE EXCEPTION 'Cannot narrow tools.slug to varchar(170): oversized data exists';
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'tools' AND column_name = 'website_url'
    ) INTO website_url_exists;
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'tools' AND column_name = 'repository_url'
    ) INTO repository_url_exists;

    IF website_url_exists AND repository_url_exists THEN
        RAISE EXCEPTION 'Both website_url and repository_url exist; reconcile their data before migration';
    END IF;

    IF website_url_exists THEN
        EXECUTE 'SELECT EXISTS (SELECT 1 FROM tools WHERE length(website_url) > 500)'
            INTO oversized_value_exists;
        IF oversized_value_exists THEN
            RAISE EXCEPTION 'Cannot rename website_url to repository_url varchar(500): oversized data exists';
        END IF;
    END IF;

    IF repository_url_exists THEN
        EXECUTE 'SELECT EXISTS (SELECT 1 FROM tools WHERE length(repository_url) > 500)'
            INTO oversized_value_exists;
        IF oversized_value_exists THEN
            RAISE EXCEPTION 'Cannot narrow repository_url to varchar(500): oversized data exists';
        END IF;
    END IF;
END $$;

ALTER TABLE categories
    ALTER COLUMN description TYPE VARCHAR(500);

ALTER TABLE tools
    ADD COLUMN IF NOT EXISTS short_description VARCHAR(300),
    ADD COLUMN IF NOT EXISTS view_count BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'tools' AND column_name = 'website_url'
    ) THEN
        ALTER TABLE tools RENAME COLUMN website_url TO repository_url;
    END IF;
END $$;

ALTER TABLE tools
    ADD COLUMN IF NOT EXISTS repository_url VARCHAR(500);

UPDATE tools
SET short_description = LEFT(description, 300)
WHERE short_description IS NULL;

ALTER TABLE tools
    ALTER COLUMN name TYPE VARCHAR(150),
    ALTER COLUMN slug TYPE VARCHAR(170),
    ALTER COLUMN short_description SET NOT NULL,
    ALTER COLUMN status TYPE VARCHAR(30),
    ALTER COLUMN repository_url TYPE VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_tools_category_id ON tools(category_id);
CREATE INDEX IF NOT EXISTS idx_tools_status ON tools(status);