param(
    [Parameter(Mandatory=$true)][string]$Backup,
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin'
)
$ErrorActionPreference = 'Stop'
# Uses only the disposable runner environment. Never accept SUPABASE_DB_* here.
$target = $env:PRIMESKILL_TEST_DB_URL
if ($target -notmatch '^jdbc:postgresql://(127\.0\.0\.1|localhost):([0-9]+)/primeskill_test_[a-z0-9_]+$') {
    throw 'Rehearsal requires the guarded disposable loopback runner.'
}
$pgPort = $Matches[2]
$database = 'primeskill_test_srestore_' + [guid]::NewGuid().ToString('N')
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$oldPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = $env:PRIMESKILL_TEST_DB_PASSWORD
    $connection = @('-h','127.0.0.1','-p',$pgPort,'-U',$env:PRIMESKILL_TEST_DB_USERNAME)
    & (Join-Path $PostgresBin 'createdb.exe') @connection $database
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create disposable restore database.' }
    # pg_dump includes CREATE SCHEMA public; only remove the empty default schema
    # of this newly created, guarded disposable database (no CASCADE).
    & (Join-Path $PostgresBin 'psql.exe') @connection -d $database -v ON_ERROR_STOP=1 -c 'DROP SCHEMA public'
    if ($LASTEXITCODE -ne 0) { throw 'New disposable database public schema was not empty.' }
    & (Join-Path $PostgresBin 'pg_restore.exe') @connection -d $database --no-owner --no-privileges --exit-on-error $Backup
    if ($LASTEXITCODE -ne 0) { throw 'Production public-schema backup restore failed.' }
    # The retained backup predates the already-approved V7/V8/B1 production rollout.
    # Reconstruct that baseline only inside this disposable restore database.
    foreach ($migration in @('doc/sql/V7__align_existing_tool_catalog.sql','doc/sql/V8__limit_review_comment_length.sql')) {
        & (Join-Path $PostgresBin 'psql.exe') @connection -d $database -v ON_ERROR_STOP=1 --single-transaction -f (Join-Path $repo $migration)
        if ($LASTEXITCODE -ne 0) { throw 'Restored baseline migration failed.' }
    }
    & (Join-Path $PostgresBin 'psql.exe') @connection -d $database -v ON_ERROR_STOP=1 -f (Join-Path $repo 'doc/sql/drafts/B1__add_tool_review_revision.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Restored B1 baseline migration failed.' }
    $counts = "SELECT json_object_agg(name, n ORDER BY name) FROM (SELECT 'users' name,count(*) n FROM users UNION ALL SELECT 'user_profiles',count(*) FROM user_profiles UNION ALL SELECT 'categories',count(*) FROM categories UNION ALL SELECT 'tags',count(*) FROM tags UNION ALL SELECT 'tools',count(*) FROM tools UNION ALL SELECT 'tool_versions',count(*) FROM tool_versions UNION ALL SELECT 'tool_tags',count(*) FROM tool_tags UNION ALL SELECT 'reviews',count(*) FROM reviews) t"
    $before = & (Join-Path $PostgresBin 'psql.exe') @connection -d $database -At -v ON_ERROR_STOP=1 -c $counts
    if ($LASTEXITCODE -ne 0) { throw 'Cannot capture business-table counts.' }
    & (Join-Path $PostgresBin 'psql.exe') @connection -d $database -v ON_ERROR_STOP=1 -f (Join-Path $repo 'doc/sql/drafts/S1__add_shared_http_sessions.sql')
    if ($LASTEXITCODE -ne 0) { throw 'S1 rehearsal failed.' }
    $after = & (Join-Path $PostgresBin 'psql.exe') @connection -d $database -At -v ON_ERROR_STOP=1 -c $counts
    if ($LASTEXITCODE -ne 0 -or "$before" -ne "$after") { throw 'Business-table counts changed.' }
    Write-Host "Restored public backup + S1 passed; all eight business table counts unchanged: $after"
    & (Join-Path $PostgresBin 'psql.exe') @connection -d $database -v ON_ERROR_STOP=1 -f (Join-Path $repo 'doc/sql/drafts/S1__add_shared_http_sessions.sql')
    if ($LASTEXITCODE -eq 0) { throw 'S1 unexpectedly allowed a rerun.' }
    $preserved = & (Join-Path $PostgresBin 'psql.exe') @connection -d $database -At -v ON_ERROR_STOP=1 -c $counts
    if ($LASTEXITCODE -ne 0 -or "$before" -ne "$preserved") { throw 'Failed rerun changed business tables.' }
    $sessions = & (Join-Path $PostgresBin 'psql.exe') @connection -d $database -At -v ON_ERROR_STOP=1 -c "select count(*) from pg_tables where schemaname='public' and tablename in ('spring_session','spring_session_attributes')"
    if ($LASTEXITCODE -ne 0 -or "$sessions" -ne '2') { throw 'Failed rerun changed session objects.' }
    Write-Host 'S1 rerun refused as intended; existing objects preserved.'
} finally { $env:PGPASSWORD = $oldPassword }
