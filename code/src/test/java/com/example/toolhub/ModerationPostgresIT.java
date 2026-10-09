package com.example.toolhub;

import com.example.toolhub.support.PostgresTestDatabaseGuard;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** Reuses the query-budget regression against PostgreSQL, including its real count query. */
@SpringBootTest(properties = "spring.datasource.url=${PRIMESKILL_TEST_DB_URL}")
@ActiveProfiles(profiles = "postgres-test", inheritProfiles = false)
@ContextConfiguration(initializers = PostgresTestDatabaseGuard.class)
class ModerationPostgresIT extends Sprint3PerformanceTest {
}
