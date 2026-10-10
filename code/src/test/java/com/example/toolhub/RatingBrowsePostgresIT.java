package com.example.toolhub;

import com.example.toolhub.support.PostgresTestDatabaseGuard;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties = "spring.datasource.url=${PRIMESKILL_TEST_DB_URL}")
@AutoConfigureMockMvc
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = PostgresTestDatabaseGuard.class)
class RatingBrowsePostgresIT extends RatingBrowseContract {}
