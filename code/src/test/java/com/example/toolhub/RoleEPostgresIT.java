package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.toolhub.support.PostgresTestDatabaseGuard;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** Replays the complete HTTP/session/state/concurrency suite on an actual PostgreSQL server. */
@SpringBootTest(properties = "spring.datasource.url=${PRIMESKILL_TEST_DB_URL}")
@ActiveProfiles(profiles = "postgres-test", inheritProfiles = false)
@ContextConfiguration(initializers = PostgresTestDatabaseGuard.class)
class RoleEPostgresIT extends RoleEFlowIntegrationTest {
    @Autowired DataSource dataSource;

    @Test
    void connectedDatabaseIsPostgreSQLAndDisposable() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("select current_database()")) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            assertTrue(result.next());
            assertTrue(result.getString(1).startsWith("primeskill_test_"));
        }
    }
}
