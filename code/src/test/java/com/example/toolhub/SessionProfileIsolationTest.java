package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:session_isolation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class SessionProfileIsolationTest {
    @Autowired ApplicationContext context;

    @Test void localProfileRetainsServletSessions() {
        assertFalse(context.containsBean("springSessionRepositoryFilter"));
    }

    @Test void deploymentConfigurationExistsButIsNotActiveLocally() throws Exception {
        var configuration = Class.forName("com.example.toolhub.config.JdbcSessionConfiguration");
        assertEquals(0, context.getBeansOfType(configuration).size());
    }
}
