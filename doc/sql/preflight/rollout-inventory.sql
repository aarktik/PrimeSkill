-- Read-only evidence only. Operator must confirm database/schema/search_path first.
-- Assumes the agreed tables exist; fail rather than inspect a different schema silently.
BEGIN READ ONLY;
DO $inventory$
DECLARE
    intended text := current_schema();
    required_table text;
    qualified oid;
BEGIN
    IF intended IS NULL THEN RAISE EXCEPTION 'Intended schema is not visible'; END IF;
    FOREACH required_table IN ARRAY ARRAY['users','user_profiles','categories','tools','tags','tool_tags','reviews','tool_versions']
    LOOP
        qualified := to_regclass(format('%I.%I', intended, required_table));
        IF qualified IS NULL OR NOT EXISTS (SELECT 1 FROM pg_class WHERE oid=qualified AND relkind IN ('r','p')) THEN
            RAISE EXCEPTION 'Required table missing from intended schema: %', required_table;
        END IF;
        IF to_regclass(required_table) IS DISTINCT FROM qualified THEN
            RAISE EXCEPTION 'Table resolution does not match intended schema: %', required_table;
        END IF;
    END LOOP;
END $inventory$;
SELECT current_database() AS database, current_schema() AS schema,
       current_setting('server_version') AS postgres_version;
SHOW search_path;
SELECT to_regclass('flyway_schema_history') AS visible_flyway_history;
SELECT table_name,column_name,data_type,character_maximum_length,is_nullable,column_default
FROM information_schema.columns
WHERE table_schema=current_schema()
  AND table_name IN ('users','user_profiles','categories','tools','tags','tool_tags','reviews','tool_versions')
ORDER BY table_name,ordinal_position;
SELECT 'tools' AS table_name,count(*) FROM tools
UNION ALL SELECT 'reviews',count(*) FROM reviews
UNION ALL SELECT 'categories',count(*) FROM categories
UNION ALL SELECT 'tags',count(*) FROM tags
UNION ALL SELECT 'tool_tags',count(*) FROM tool_tags
UNION ALL SELECT 'tool_versions',count(*) FROM tool_versions;
SELECT count(*) FILTER (WHERE char_length(name)>150) AS oversized_names,
       count(*) FILTER (WHERE char_length(slug)>170) AS oversized_slugs,
       count(*) FILTER (WHERE char_length(to_jsonb(t)->>'short_description')>300) AS oversized_short_descriptions,
       count(*) FILTER (WHERE char_length(to_jsonb(t)->>'website_url')>500) AS oversized_website_urls,
       count(*) FILTER (WHERE char_length(to_jsonb(t)->>'repository_url')>500) AS oversized_repository_urls,
       count(*) FILTER (WHERE description IS NULL AND to_jsonb(t)->>'short_description' IS NULL) AS missing_backfill_source,
       count(*) FILTER (WHERE (to_jsonb(t)->>'review_revision')::numeric<0) AS negative_revisions
FROM tools t;
SELECT count(*) AS oversized_category_descriptions FROM categories WHERE char_length(description)>500;
SELECT count(*) AS oversized_review_comments FROM reviews WHERE char_length(comment)>2000;
SELECT table_name,column_name FROM information_schema.columns
WHERE table_schema=current_schema() AND table_name='tools'
  AND column_name IN ('website_url','repository_url') ORDER BY column_name;
SELECT conrelid::regclass AS table_name,conname,contype,convalidated,pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE connamespace=(SELECT oid FROM pg_namespace WHERE nspname=current_schema())
ORDER BY conrelid::regclass::text,conname;
SELECT tablename,indexname,indexdef FROM pg_indexes
WHERE schemaname=current_schema() ORDER BY tablename,indexname;
ROLLBACK;
