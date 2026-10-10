package com.example.toolhub.support;

import com.example.toolhub.ToolHubApplication;
import java.sql.DriverManager;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Independent HTTP servers; setup refuses remote databases before any SQL. */
public final class JdbcSessionTestInstances implements AutoCloseable {
    public final String url;
    public final String username = System.getenv("PRIMESKILL_TEST_DB_USERNAME");
    public final String password = System.getenv("PRIMESKILL_TEST_DB_PASSWORD");
    public ConfigurableApplicationContext a, b;

    public JdbcSessionTestInstances() throws Exception {
        String base = System.getenv("PRIMESKILL_TEST_DB_URL");
        PostgresTestDatabaseGuard.validateJdbcUrl(base);
        String name = "primeskill_test_session_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(base, username, password);
             var statement = connection.createStatement()) { statement.execute("CREATE DATABASE " + name); }
        url = base.substring(0, base.lastIndexOf('/') + 1) + name;
        PostgresTestDatabaseGuard.validateJdbcUrl(url);
        try (var connection = DriverManager.getConnection(url, username, password)) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            try (var statement = connection.createStatement()) {
                statement.execute("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon; END IF; IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated; END IF; END $$");
                // Deliberately permissive defaults prove that S1 removes inherited API grants.
                statement.execute("ALTER DEFAULT PRIVILEGES GRANT ALL ON TABLES TO anon, authenticated");
                statement.execute(java.nio.file.Files.readString(java.nio.file.Path.of("../doc/sql/drafts/S1__add_shared_http_sessions.sql")));
            }
        }
        try { a = start(); b = start(); } catch (Exception failure) { close(); throw failure; }
    }

    public ConfigurableApplicationContext start() {
        return start(false);
    }

    public ConfigurableApplicationContext start(boolean deploymentProfile) {
        var application = new SpringApplication(ToolHubApplication.class);
        application.addInitializers(new PostgresTestDatabaseGuard());
        return application.run("--spring.profiles.active=postgres-test," + (deploymentProfile ? "vercel" : "jdbc-session-test"),
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + username,
                "--spring.datasource.password=" + password, "--spring.sql.init.mode=never",
                "--server.address=127.0.0.1", "--server.port=0", "--server.servlet.session.cookie.secure=" + deploymentProfile,
                "--spring.main.banner-mode=off", "--logging.level.root=WARN");
    }

    public String address(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + ((ServletWebServerApplicationContext) context).getWebServer().getPort();
    }

    @Override public void close() {
        if (b != null) b.close();
        if (a != null) a.close();
    }
}
