package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.toolhub.support.PostgresTestDatabaseGuard;
import java.nio.charset.StandardCharsets;
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

/** Runs D's unchanged draft snapshot exclusively against private disposable schemas. */
@SpringBootTest(properties = "spring.datasource.url=${PRIMESKILL_TEST_DB_URL}")
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = PostgresTestDatabaseGuard.class)
class ReviewCommentMigrationPostgresIT {
    @Autowired DataSource dataSource;
    private Connection connection;
    private String schema;
    private String migration;

    @BeforeEach
    void createIsolatedReviewsFixture() throws Exception {
        try (var resource = getClass().getResourceAsStream("/migrations/role-d/V8__limit_review_comment_length.sql")) {
            assertNotNull(resource);
            migration = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        connection = dataSource.getConnection();
        schema = "review_migration_test_" + UUID.randomUUID().toString().replace("-", "");
        sql("CREATE SCHEMA " + schema);
        sql("SET search_path TO " + schema);
        sql("CREATE TABLE reviews(id BIGSERIAL PRIMARY KEY, comment TEXT, rating SMALLINT CHECK(rating BETWEEN 1 AND 5))");
    }

    @AfterEach
    void cleanOwnSchemaAndResetConnection() throws Exception {
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
    @ValueSource(strings = {"null", "empty", "spaces", "ascii1999", "ascii2000", "thai2000", "emoji2000"})
    void validLegacyCommentsArePreservedAndConstraintValidated(String kind) throws Exception {
        String comment = switch (kind) {
            case "null" -> null;
            case "empty" -> "";
            case "spaces" -> "   ";
            case "ascii1999" -> "a".repeat(1999);
            case "ascii2000" -> "a".repeat(2000);
            case "thai2000" -> "ก".repeat(2000);
            default -> "😀".repeat(2000);
        };
        insert(comment);
        migrate();
        assertEquals(comment, value("SELECT comment FROM reviews WHERE id=1"));
        assertEquals("true", value("SELECT convalidated::text FROM pg_constraint WHERE conrelid='reviews'::regclass "
                + "AND conname='ck_reviews_comment_length'"));
        assertEquals("1", value("SELECT count(*) FROM reviews"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "ก", "😀"})
    void oversizedLegacyDataStopsMigrationWithoutTruncatingOrInstallingConstraint(String character) throws Exception {
        String original = character.repeat(2001);
        insert(original);
        connection.setAutoCommit(false);
        SQLException failure = assertThrows(SQLException.class, () -> sql(migration));
        assertTrue(failure.getMessage().contains("oversized data exists"), failure.getMessage());
        connection.rollback();
        connection.setAutoCommit(true);
        assertEquals(original, value("SELECT comment FROM reviews WHERE id=1"));
        assertEquals("0", constraintCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "ก", "😀"})
    void constraintRejectsOversizedInsertsAndUpdates(String character) throws Exception {
        migrate();
        insert(character.repeat(2000));
        SQLException insertFailure = assertThrows(SQLException.class, () -> insert(character.repeat(2001)));
        assertEquals("23514", insertFailure.getSQLState());
        try (var update = connection.prepareStatement("UPDATE reviews SET comment=? WHERE id=1")) {
            update.setString(1, character.repeat(2001));
            SQLException updateFailure = assertThrows(SQLException.class, update::executeUpdate);
            assertEquals("23514", updateFailure.getSQLState());
        }
        assertEquals(character.repeat(2000), value("SELECT comment FROM reviews WHERE id=1"));
        assertEquals("1", value("SELECT count(*) FROM reviews"));
    }

    @Test
    void emptyTableAndRerunAreSafeAndKeepOtherConstraints() throws Exception {
        migrate();
        migrate();
        assertEquals("1", constraintCount());
        assertEquals("0", value("SELECT count(*) FROM reviews"));
        SQLException ratingFailure = assertThrows(SQLException.class,
                () -> sql("INSERT INTO reviews(comment,rating) VALUES('valid comment',6)"));
        assertEquals("23514", ratingFailure.getSQLState());
    }

    @Test
    void existingNotValidConstraintIsValidated() throws Exception {
        insert("valid existing comment");
        sql("ALTER TABLE reviews ADD CONSTRAINT ck_reviews_comment_length "
                + "CHECK(comment IS NULL OR char_length(comment)<=2000) NOT VALID");
        assertEquals("false", value("SELECT convalidated::text FROM pg_constraint WHERE conrelid='reviews'::regclass "
                + "AND conname='ck_reviews_comment_length'"));
        migrate();
        assertEquals("true", value("SELECT convalidated::text FROM pg_constraint WHERE conrelid='reviews'::regclass "
                + "AND conname='ck_reviews_comment_length'"));
        assertEquals("valid existing comment", value("SELECT comment FROM reviews"));
    }

    @Test
    void callerRollbackUndoesAddedConstraintAndKeepsLegacyData() throws Exception {
        insert("original");
        connection.setAutoCommit(false);
        sql(migration);
        assertEquals("1", constraintCount());
        connection.rollback();
        connection.setAutoCommit(true);
        assertEquals("0", constraintCount());
        assertEquals("original", value("SELECT comment FROM reviews"));
    }

    private void migrate() throws Exception {
        connection.setAutoCommit(false);
        sql(migration);
        connection.commit();
        connection.setAutoCommit(true);
    }

    private void insert(String comment) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO reviews(comment,rating) VALUES(?,5)")) {
            statement.setQueryTimeout(15);
            statement.setString(1, comment);
            statement.executeUpdate();
        }
    }

    private String constraintCount() throws Exception {
        return value("SELECT count(*) FROM pg_constraint WHERE conrelid='reviews'::regclass "
                + "AND conname='ck_reviews_comment_length'");
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
