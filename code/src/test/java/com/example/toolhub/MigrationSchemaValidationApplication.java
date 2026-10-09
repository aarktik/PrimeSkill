package com.example.toolhub;

import com.example.toolhub.support.PostgresTestDatabaseGuard;
import org.springframework.boot.SpringApplication;

/** Explicit rehearsal launcher: validates migrated schema without creating missing tables. */
public class MigrationSchemaValidationApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(ToolHubApplication.class);
        app.addInitializers(new PostgresTestDatabaseGuard());
        app.setAdditionalProfiles("postgres-test");
        try (var context = app.run(args)) {
            if (!context.isActive()) throw new IllegalStateException("Application did not start");
            System.out.println("MIGRATION_SCHEMA_STARTUP_PASS");
        }
    }
}
