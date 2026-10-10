-- Draft only: assign the forward migration version after inspecting target history.
-- Run the complete file with the approved schema/search_path; PostgreSQL only.
BEGIN;
LOCK TABLE tools IN ACCESS EXCLUSIVE MODE;

DO $b1$
DECLARE
    column_type oid;
    required boolean;
    default_expression text;
    definition text;
    validated boolean;
BEGIN
    SELECT a.atttypid, a.attnotnull, pg_get_expr(d.adbin, d.adrelid)
      INTO column_type, required, default_expression
      FROM pg_attribute a
      LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
     WHERE a.attrelid='tools'::regclass AND a.attname='review_revision' AND NOT a.attisdropped;
    IF FOUND THEN
        IF column_type <> 'bigint'::regtype OR NOT required
           OR coalesce(regexp_replace(default_expression, '[[:space:]()]', '', 'g'), '') NOT IN ('0', '0::bigint') THEN
            RAISE EXCEPTION 'B1 preflight: incompatible existing review_revision column; review forward repair';
        END IF;
    ELSE
        ALTER TABLE tools ADD COLUMN review_revision BIGINT NOT NULL DEFAULT 0;
    END IF;

    IF EXISTS(SELECT 1 FROM tools WHERE review_revision IS NULL OR review_revision < 0) THEN
        RAISE EXCEPTION 'B1 preflight: invalid legacy revision values; no data was repaired';
    END IF;

    SELECT pg_get_constraintdef(oid), convalidated INTO definition, validated
      FROM pg_constraint
     WHERE conrelid='tools'::regclass AND conname='ck_tools_review_revision_nonnegative';
    IF FOUND THEN
        IF NOT validated OR regexp_replace(definition, '[[:space:]()]', '', 'g') <> 'CHECKreview_revision>=0' THEN
            RAISE EXCEPTION 'B1 preflight: incompatible existing revision constraint; review forward repair';
        END IF;
    ELSE
        ALTER TABLE tools ADD CONSTRAINT ck_tools_review_revision_nonnegative CHECK (review_revision >= 0);
    END IF;
END
$b1$;
COMMIT;
