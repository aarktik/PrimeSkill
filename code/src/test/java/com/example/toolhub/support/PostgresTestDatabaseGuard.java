package com.example.toolhub.support;

import java.net.URI;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Runs before SQL initialization because integration-test cleanup deletes rows. */
public class PostgresTestDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        validateJdbcUrl(context.getEnvironment().getProperty("spring.datasource.url"));
    }

    static void validateJdbcUrl(String url) {
        String message = "PostgreSQL tests require a loopback JDBC URL with an explicit port and "
                + "a disposable database named primeskill_test_*. Remote/shared databases are refused.";
        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException(message);
        }
        try {
            URI target = URI.create(url.substring("jdbc:".length()));
            boolean loopback = "127.0.0.1".equals(target.getHost())
                    || "localhost".equalsIgnoreCase(target.getHost());
            if (!loopback || target.getPort() < 1 || target.getPort() > 65535
                    || target.getRawUserInfo() != null || target.getRawQuery() != null
                    || target.getRawFragment() != null || target.getRawPath() == null
                    || !target.getRawPath().matches("/primeskill_test_[a-z0-9_]+")) {
                throw new IllegalStateException(message);
            }
        } catch (IllegalArgumentException exception) {
            // Do not echo a URL that could contain credentials.
            throw new IllegalStateException(message);
        }
    }
}
