-- DRAFT S1: new objects only. Run complete file with ON_ERROR_STOP=1 after approval.
-- Source: org.springframework.session:spring-session-jdbc:4.1.1 / schema-postgresql.sql
-- Deviation: PRINCIPAL_NAME widened from 100 to 255 to match users.email; all other types unchanged.
-- Policy: refuse every rerun, including compatible tables; inspect instead of masking drift.
BEGIN;
SET LOCAL search_path = public, pg_catalog;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '30s';
SELECT pg_advisory_xact_lock(735197021);
DO $preflight$
BEGIN
  IF to_regclass('public.spring_session') IS NOT NULL OR
     to_regclass('public.spring_session_attributes') IS NOT NULL THEN
    RAISE EXCEPTION 'Session objects already exist; inspect schema and installation record before retrying';
  END IF;
END
$preflight$;
CREATE TABLE SPRING_SESSION (
	PRIMARY_ID CHAR(36) NOT NULL,
	SESSION_ID CHAR(36) NOT NULL,
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	EXPIRY_TIME BIGINT NOT NULL,
	PRINCIPAL_NAME VARCHAR(255),
	CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
	SESSION_PRIMARY_ID CHAR(36) NOT NULL,
	ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
	ATTRIBUTE_BYTES BYTEA NOT NULL,
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
-- Override inherited default table ACLs only for these new session tables.
REVOKE ALL ON public.spring_session, public.spring_session_attributes FROM PUBLIC;
DO $privileges$
DECLARE exposed_role text; session_table text; permission text;
BEGIN
  FOREACH exposed_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = exposed_role) THEN
      EXECUTE format('REVOKE ALL ON TABLE public.spring_session, public.spring_session_attributes FROM %I', exposed_role);
      FOREACH session_table IN ARRAY ARRAY['public.spring_session', 'public.spring_session_attributes'] LOOP
        FOREACH permission IN ARRAY ARRAY['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER'] LOOP
          IF has_table_privilege(exposed_role, session_table, permission) THEN
            RAISE EXCEPTION 'Session access remains through inherited privileges for %: % on %', exposed_role, permission, session_table;
          END IF;
        END LOOP;
      END LOOP;
    END IF;
  END LOOP;
END
$privileges$;
-- An approved dedicated application role needs SELECT/INSERT/UPDATE/DELETE on both tables.
-- Grant it separately to the operator-confirmed role, never anon/authenticated.
COMMIT;
