package com.example.toolhub.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class PostgresTestDatabaseGuardTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "", "jdbc:h2:mem:test", "jdbc:postgresql://db.example.com:5432/primeskill_test_role_e",
            "jdbc:postgresql://127.0.0.1:5432/postgres", "jdbc:postgresql://localhost:5432/toolhub",
            "jdbc:postgresql://127.0.0.1:5432/", "jdbc:postgresql:primeskill_test_role_e",
            "jdbc:postgresql://user:password@localhost:5432/primeskill_test_role_e",
            "jdbc:postgresql://localhost:5432/primeskill_test_role_e?options=-csearch_path=public",
            "jdbc:postgresql://localhost:5432/primeskill_test_role_e#fragment",
            "jdbc:postgresql://localhost:5432/primeskill_test_%72ole_e",
            "jdbc:postgresql://localhost:0/primeskill_test_role_e"
    })
    void rejectsTargetsBeforeAnyDatabaseConnection(String url) {
        assertThrows(IllegalStateException.class, () -> PostgresTestDatabaseGuard.validateJdbcUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://127.0.0.1:15432/primeskill_test_role_e",
            "jdbc:postgresql://localhost:5432/primeskill_test_ci"
    })
    void acceptsExplicitLoopbackTestDatabases(String url) {
        assertDoesNotThrow(() -> PostgresTestDatabaseGuard.validateJdbcUrl(url));
    }

    @Test
    void missingUrlIsRejected() {
        assertThrows(IllegalStateException.class, () -> PostgresTestDatabaseGuard.validateJdbcUrl(null));
    }

    @Test
    void initializerChecksTheResolvedDatasourceBeforeRefresh() {
        try (var context = new GenericApplicationContext()) {
            context.setEnvironment(new MockEnvironment()
                    .withProperty("spring.datasource.url", "jdbc:postgresql://remote:5432/primeskill_test_ci"));
            assertThrows(IllegalStateException.class, () -> new PostgresTestDatabaseGuard().initialize(context));
        }
    }
}
