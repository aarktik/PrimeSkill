package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ToolHubApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private Environment environment;

	@Test
	void contextLoads() throws Exception {
		assertEquals("jdbc:h2:mem:toolhub_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
				environment.getProperty("spring.datasource.url"));
		assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
		assertEquals("false", environment.getProperty("spring.jpa.open-in-view"));
		try (var connection = dataSource.getConnection()) {
			assertEquals("H2", connection.getMetaData().getDatabaseProductName());
			assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:toolhub_test"));
		}
	}

}

