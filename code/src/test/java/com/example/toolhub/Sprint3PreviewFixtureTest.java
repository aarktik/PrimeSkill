package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.*;
import com.example.toolhub.repository.CategoryRepository;
import com.example.toolhub.repository.UserRepository;
import com.example.toolhub.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:b1_preview_fixture;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class Sprint3PreviewFixtureTest {
    @Autowired UserRegistrationService registration;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ToolService tools;
    @Autowired ToolVersionService versions;
    @Autowired PublishingService publishing;
    @Autowired JdbcTemplate jdbc;

    @Test void previewCreatesAllThreeStagesUsingCurrentSubmissionRevision() {
        var runner = new Sprint3PreviewApplication.Fixtures().reviewAccounts(
                registration, users, categories, tools, versions, publishing);
        assertDoesNotThrow(() -> runner.run(new DefaultApplicationArguments(new String[0])));
        for (String stage : new String[]{"draft", "pending", "published"}) {
            String slug = "preview-" + stage;
            assertEquals(stage.toUpperCase(), jdbc.queryForObject(
                    "select status from tools where slug=?", String.class, slug));
            assertEquals(stage.equals("draft") ? 0L : 1L, jdbc.queryForObject(
                    "select review_revision from tools where slug=?", Long.class, slug));
        }
    }
}
