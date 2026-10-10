param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$JavaCommand = 'java'
)
# Test helper only: call inside scripts/test-postgres.ps1's disposable-cluster lifetime.
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ($env:PRIMESKILL_TEST_DB_URL -notmatch '^jdbc:postgresql://127\.0\.0\.1:(\d+)/primeskill_test_role_e$') {
    throw 'Only the loopback disposable runner database is accepted.'
}
$port = $Matches[1]
if ([string]::IsNullOrWhiteSpace($env:PRIMESKILL_TEST_DB_USERNAME) -or [string]::IsNullOrWhiteSpace($env:PRIMESKILL_TEST_DB_PASSWORD)) {
    throw 'Disposable runner credentials are required.'
}
$connectionArgs = @('-h','127.0.0.1','-p',$port,'-U',$env:PRIMESKILL_TEST_DB_USERNAME)
$runId = [guid]::NewGuid().ToString('N')
$legacyDb = 'primeskill_test_rollout_' + $runId
$restoredDb = 'primeskill_test_restore_' + $runId
$evidence = Join-Path $repo "code/target/migration-rollout/$runId"
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$oldPassword = $env:PGPASSWORD
$oldEncoding = $env:PGCLIENTENCODING
$oldUrl = $env:PRIMESKILL_TEST_DB_URL
$env:PGPASSWORD = $env:PRIMESKILL_TEST_DB_PASSWORD
$env:PGCLIENTENCODING = 'UTF8'
$created = @()
function Native([string]$binary, [string[]]$arguments) {
    & (Join-Path $PostgresBin ($binary + '.exe')) @arguments
    if ($LASTEXITCODE -ne 0) { throw "$binary failed; stop rehearsal" }
}
function Query([string]$db, [string]$sql) {
    $result = Native 'psql' ($connectionArgs + @('-d',$db,'-v','ON_ERROR_STOP=1','-Atc',$sql))
    return ($result -join "`n").Trim()
}
function Equal([string]$expected, [string]$actual, [string]$label) {
    if ($expected -cne $actual) { throw "Validation failed: $label" }
}
function Migrate([string]$db) {
    # V7/V8 are caller-transactional; B1 contains its own complete transaction.
    foreach ($draft in @('doc/sql/V7__align_existing_tool_catalog.sql','doc/sql/V8__limit_review_comment_length.sql')) {
        Native 'psql' ($connectionArgs + @('-d',$db,'-v','ON_ERROR_STOP=1','--single-transaction','-f',(Join-Path $repo $draft))) | Out-Null
    }
    Native 'psql' ($connectionArgs + @('-d',$db,'-v','ON_ERROR_STOP=1','-f',(Join-Path $repo 'doc/sql/drafts/B1__add_tool_review_revision.sql'))) | Out-Null
}
function Contents([string]$db) {
    # Exact original populated columns and relationships; derived/new columns are checked separately.
    Query $db "select json_build_array((select json_agg(u order by id) from users u),(select json_agg(p order by id) from user_profiles p),(select json_agg(c order by id) from categories c),(select json_agg(row(id,name,slug,description,owner_id,category_id,status,created_at,updated_at) order by id) from tools),(select json_agg(r order by id) from reviews r),(select json_agg(t order by id) from tags t),(select json_agg(tt order by tool_id,tag_id) from tool_tags tt),(select json_agg(v order by id) from tool_versions v))::text"
}
function Startup([string]$db, [string]$phase) {
    $env:PRIMESKILL_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$port/$db"
    $runtime = (Get-Content -Raw (Join-Path $repo 'code/target/rollout-classpath.txt')).Trim()
    $classpath = @((Join-Path $repo 'code/target/test-classes'),(Join-Path $repo 'code/target/classes'),$runtime) -join [IO.Path]::PathSeparator
    & $JavaCommand -cp $classpath com.example.toolhub.MigrationSchemaValidationApplication '--spring.sql.init.mode=never' '--spring.jpa.hibernate.ddl-auto=validate' '--server.address=127.0.0.1' '--server.port=0' '--server.servlet.session.cookie.secure=false' *> (Join-Path $evidence "$phase-startup.log")
    if ($LASTEXITCODE -ne 0) { throw "Hibernate/application startup failed: $db" }
    if (-not (Select-String -Path (Join-Path $evidence "$phase-startup.log") -Pattern 'MIGRATION_SCHEMA_STARTUP_PASS' -Quiet)) { throw 'Startup marker missing' }
}
try {
    foreach ($db in @($legacyDb,$restoredDb)) {
        Native 'createdb' ($connectionArgs + @($db)); $created += $db
    }
    $schemaFile = Join-Path $repo 'code/src/main/resources/schema.sql'
    Native 'psql' ($connectionArgs + @('-d',$legacyDb,'-v','ON_ERROR_STOP=1','--single-transaction','-f',$schemaFile)) | Out-Null
    # Empty database startup is schema initialization evidence, not proof of a migration baseline.
    Startup $legacyDb 'empty'
    $seedFile = Join-Path $evidence 'legacy-fixture.sql'
    [IO.File]::WriteAllText($seedFile, "alter table tools drop column short_description, drop column view_count, drop column review_revision; alter table tools rename column repository_url to website_url; alter table tools alter column name type text, alter column slug type text; alter table categories alter column description type text; alter table reviews drop constraint ck_reviews_comment_length; insert into users(id,email,password_hash) values(1,'fixture@test.invalid','not-a-login'); insert into categories(id,name,slug,description) values(1,'หมวด','fixture','ข้อมูลเดิม'); insert into tools(id,name,slug,description,website_url,owner_id,category_id,status) values(1,'เครื่องมือ','fixture',repeat('ก',350),'https://example.test/legacy',1,1,'PENDING'); insert into tags(id,name,slug) values(1,'Tag','fixture'); insert into tool_tags values(1,1); insert into reviews(id,user_id,tool_id,rating,comment) values(1,1,1,5,'รีวิวเดิม'); insert into tool_versions(id,tool_id,version,release_notes) values(1,1,'1.0','ข้อมูลรุ่นเดิม');", [Text.UTF8Encoding]::new($false))
    Native 'psql' ($connectionArgs + @('-d',$legacyDb,'-v','ON_ERROR_STOP=1','--single-transaction','-f',$seedFile)) | Out-Null
    $before = Contents $legacyDb
    $relations = Query $legacyDb "select string_agg(conrelid::regclass||':'||conname||':'||pg_get_constraintdef(oid),',' order by conrelid::regclass::text,conname) from pg_constraint where connamespace='public'::regnamespace and contype in ('f','u','p')"
    Native 'psql' ($connectionArgs + @('-d',$legacyDb,'-v','ON_ERROR_STOP=1','-f',(Join-Path $repo 'doc/sql/preflight/rollout-inventory.sql'))) | Set-Content (Join-Path $evidence 'legacy-inventory.txt')
    $dump = Join-Path $evidence 'legacy.dump'
    Native 'pg_dump' ($connectionArgs + @('-d',$legacyDb,'--format=custom',"--file=$dump"))
    Migrate $legacyDb
    Equal $before (Contents $legacyDb) 'original data and associations after migration'
    Equal $relations (Query $legacyDb "select string_agg(conrelid::regclass||':'||conname||':'||pg_get_constraintdef(oid),',' order by conrelid::regclass::text,conname) from pg_constraint where connamespace='public'::regnamespace and contype in ('f','u','p')") 'foreign keys, unique keys and primary keys unchanged'
    Equal '0:0:300:https://example.test/legacy' (Query $legacyDb "select review_revision||':'||view_count||':'||char_length(short_description)||':'||repository_url from tools where id=1") 'new fields and renamed URL'
    $after = Query $legacyDb "select row_to_json(t)::text from tools t where id=1"
    Migrate $legacyDb
    Equal $after (Query $legacyDb "select row_to_json(t)::text from tools t where id=1") 'rerun preserves complete tool row'
    Native 'psql' ($connectionArgs + @('-d',$legacyDb,'-v','ON_ERROR_STOP=1','-f',(Join-Path $repo 'doc/sql/preflight/rollout-inventory.sql'))) | Set-Content (Join-Path $evidence 'migrated-inventory.txt')
    Startup $legacyDb 'migrated'
    $restoreStart = Get-Date
    Native 'pg_restore' ($connectionArgs + @('--no-owner','--exit-on-error','-d',$restoredDb,$dump))
    Equal $before (Contents $restoredDb) 'restored original contents'
    Equal '0' (Query $restoredDb "select count(*) from information_schema.columns where table_schema='public' and table_name='tools' and column_name='review_revision'") 'restored legacy schema'
    Migrate $restoredDb
    Equal $before (Contents $restoredDb) 'restored then re-migrated contents'
    Startup $restoredDb 'restored-remigrated'
    $hashes = @('doc/sql/V7__align_existing_tool_catalog.sql','doc/sql/V8__limit_review_comment_length.sql','doc/sql/drafts/B1__add_tool_review_revision.sql','code/src/main/resources/schema.sql') | ForEach-Object {
        $hash = Get-FileHash (Join-Path $repo $_) -Algorithm SHA256
        [ordered]@{file=$_;sha256=$hash.Hash}
    }
    [ordered]@{status='PASS';scope='local representative full-schema fixture, not shared DB or approved production runner';restoreAndRemigrateSeconds=[math]::Round(((Get-Date)-$restoreStart).TotalSeconds,2);drafts=$hashes;backupSha256=(Get-FileHash $dump -Algorithm SHA256).Hash} | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $evidence 'summary.json')
    Write-Host "Migration rollout rehearsal PASS; evidence: $evidence"
} finally {
    $env:PRIMESKILL_TEST_DB_URL = $oldUrl
    try { foreach ($db in $created) {
        if ($db -notmatch '^primeskill_test_(rollout|restore)_[a-f0-9]{32}$') { throw 'Refusing cleanup of unexpected database name' }
        Native 'dropdb' ($connectionArgs + @($db))
    } } finally { $env:PGPASSWORD = $oldPassword; $env:PGCLIENTENCODING = $oldEncoding }
}
