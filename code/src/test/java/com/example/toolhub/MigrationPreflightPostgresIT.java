package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.toolhub.support.PostgresTestDatabaseGuard;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** Exercises the draft SQL only in a private schema of the guarded disposable database. */
@SpringBootTest(properties = "spring.datasource.url=${PRIMESKILL_TEST_DB_URL}")
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = PostgresTestDatabaseGuard.class)
class MigrationPreflightPostgresIT {
    @Autowired DataSource dataSource;
    private Connection connection;
    private String schema;
    private String migration;

    @BeforeEach
    void prepareLegacyFixture() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("doc/sql/V7__align_existing_tool_catalog.sql"))) {
            root = root.getParent();
        }
        assertNotNull(root, "Run from the repository so the real draft SQL is available");
        migration = Files.readString(root.resolve("doc/sql/V7__align_existing_tool_catalog.sql"));
        connection = dataSource.getConnection();
        schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
        sql("CREATE SCHEMA " + schema);
        sql("SET search_path TO " + schema);
        sql("CREATE TABLE categories(id BIGINT PRIMARY KEY, description TEXT)");
        sql("CREATE TABLE tools(id BIGINT PRIMARY KEY, category_id BIGINT REFERENCES categories(id), "
                + "name TEXT NOT NULL, slug TEXT NOT NULL UNIQUE, description TEXT, status VARCHAR(255))");
        sql("INSERT INTO categories VALUES(1, 'หมวดเดิม')");
        sql("INSERT INTO tools VALUES(1, 1, 'เครื่องมือเดิม', 'legacy-tool', repeat('ก',350), 'PUBLISHED')");
    }

    @AfterEach
    void removeOnlyPrivateSchemaAndResetPooledConnection() throws Exception {
        if (connection != null) {
            try (var ownedConnection = connection) {
                if (!connection.getAutoCommit()) connection.rollback();
                connection.setAutoCommit(true);
                sql("SET search_path TO public");
                if (schema != null) sql("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"website_url", "repository_url", "none"})
    void migratesLegacyUrlVariantsWithoutLosingData(String urlColumn) throws Exception {
        if (!urlColumn.equals("none")) {
            sql("ALTER TABLE tools ADD COLUMN " + urlColumn + " TEXT");
            sql("UPDATE tools SET " + urlColumn + " = 'https://example.test/legacy'");
        }
        migrate();
        assertEquals("1", value("SELECT count(*) FROM tools"));
        assertEquals("เครื่องมือเดิม", value("SELECT name FROM tools"));
        assertEquals("350", value("SELECT length(description) FROM tools"));
        assertEquals("ก".repeat(300), value("SELECT short_description FROM tools"));
        assertEquals("0", value("SELECT view_count FROM tools"));
        assertEquals(urlColumn.equals("none") ? null : "https://example.test/legacy",
                value("SELECT repository_url FROM tools"));
        assertEquals("0", columnCount("website_url"));
        assertEquals("2", value("SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() "
                + "AND indexname IN ('idx_tools_category_id','idx_tools_status')"));
        assertEquals("1", value("SELECT count(*) FROM tools t JOIN categories c ON c.id=t.category_id"));
    }

    @Test
    void rolloutInventoryRejectsSearchPathFallbackToPublicTables() throws Exception {
        // This schema has only tools/categories; the other tables exist in public.
        sql("SET search_path TO " + schema + ", public");
        String inventory = Files.readString(Path.of("../doc/sql/preflight/rollout-inventory.sql"));
        SQLException failure = assertThrows(SQLException.class, () -> sql(inventory));
        sql("ROLLBACK");
        assertTrue(failure.getMessage().contains("Required table missing from intended schema"), failure.getMessage());
        assertEquals("1", value("SELECT count(*) FROM " + schema + ".tools"));
    }

    @Test
    void rerunningDraftPreservesEditedValuesAndViewCount() throws Exception {
        migrate();
        sql("UPDATE tools SET short_description='ข้อความที่ผู้ใช้แก้', view_count=9");
        migrate();
        assertEquals("ข้อความที่ผู้ใช้แก้", value("SELECT short_description FROM tools"));
        assertEquals("9", value("SELECT view_count FROM tools"));
    }

    @Test
    void alignsExistingTextShortDescriptionWithoutReplacingContent() throws Exception {
        sql("ALTER TABLE tools ADD COLUMN short_description TEXT");
        sql("UPDATE tools SET short_description='ข้อความเดิม'");
        migrate();
        assertEquals("ข้อความเดิม", value("SELECT short_description FROM tools"));
        assertEquals("300", value("SELECT character_maximum_length FROM information_schema.columns "
                + "WHERE table_schema=current_schema() AND table_name='tools' AND column_name='short_description'"));
    }

    @Test
    void rejectsExistingOversizedShortDescriptionInsteadOfKeepingInvalidSchema() throws Exception {
        sql("ALTER TABLE tools ADD COLUMN short_description TEXT");
        sql("UPDATE tools SET short_description=repeat('ก',301)");
        connection.setAutoCommit(false);
        SQLException failure = assertThrows(SQLException.class, () -> sql(migration));
        assertTrue(failure.getMessage().contains("short_description"), failure.getMessage());
        connection.rollback();
        connection.setAutoCommit(true);
        assertEquals("301", value("SELECT length(short_description) FROM tools"));
        assertEquals("0", columnCount("view_count"));
    }

    @Test
    void refusesBothUrlColumnsWithoutChangingEitherValue() throws Exception {
        sql("ALTER TABLE tools ADD COLUMN website_url TEXT, ADD COLUMN repository_url TEXT");
        sql("UPDATE tools SET website_url='https://example.test/old', repository_url='https://example.test/new'");
        assertRejectedAndRolledBack("Both website_url and repository_url exist");
        assertEquals("https://example.test/old", value("SELECT website_url FROM tools"));
        assertEquals("https://example.test/new", value("SELECT repository_url FROM tools"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"category", "name", "slug", "website_url", "repository_url"})
    void refusesOversizedDataBeforeChangingSchema(String field) throws Exception {
        String expression = switch (field) {
            case "category" -> "UPDATE categories SET description=repeat('ก',501)";
            case "name" -> "UPDATE tools SET name=repeat('ก',151)";
            case "slug" -> "UPDATE tools SET slug=repeat('x',171)";
            default -> "ALTER TABLE tools ADD COLUMN " + field + " TEXT";
        };
        sql(expression);
        if (field.endsWith("_url")) sql("UPDATE tools SET " + field + "=repeat('x',501)");
        assertRejectedAndRolledBack("oversized data exists");
        String originalLength = switch (field) {
            case "category" -> value("SELECT length(description) FROM categories");
            default -> value("SELECT length(" + field + ") FROM tools");
        };
        assertEquals(switch (field) { case "name" -> "151"; case "slug" -> "171"; default -> "501"; }, originalLength);
    }

    @Test
    void transactionRollbackRestoresSchemaIfLaterNotNullStepFails() throws Exception {
        sql("UPDATE tools SET description=NULL");
        assertRejectedAndRolledBack("null values");
        assertEquals("1", value("SELECT count(*) FROM tools"));
        assertNull(value("SELECT description FROM tools"));
    }

    private void assertRejectedAndRolledBack(String message) throws Exception {
        connection.setAutoCommit(false);
        SQLException failure = assertThrows(SQLException.class, () -> sql(migration));
        assertTrue(failure.getMessage().contains(message), failure.getMessage());
        connection.rollback();
        connection.setAutoCommit(true);
        assertEquals("0", columnCount("short_description"));
        assertEquals("0", columnCount("view_count"));
        assertEquals("1", value("SELECT count(*) FROM tools"));
    }

    private void migrate() throws Exception {
        connection.setAutoCommit(false);
        sql(migration);
        connection.commit();
        connection.setAutoCommit(true);
    }

    private String columnCount(String column) throws Exception {
        return value("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() "
                + "AND table_name='tools' AND column_name='" + column + "'");
    }

    private void sql(String query) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(15);
            statement.execute(query);
        }
    }

    private String value(String query) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(15);
            try (var result = statement.executeQuery(query)) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }
}
