package com.example.toolhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.toolhub.domain.entity.Category;
import com.example.toolhub.domain.entity.Review;
import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.entity.User;
import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.dto.request.UpdateReviewRequest;
import com.example.toolhub.repository.CategoryRepository;
import com.example.toolhub.repository.ReviewRepository;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.repository.UserRepository;
import com.example.toolhub.support.PostgresTestDatabaseGuard;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL regressions for both rollback orders around the shared Tool lock. */
@SpringBootTest(properties = {
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = PostgresTestDatabaseGuard.class)
class ReviewServiceLockRollbackPostgresIT {

    @Autowired private ReviewService reviewService;
    @Autowired private ToolRepository toolRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;

    @ParameterizedTest
    @ValueSource(strings = {"create", "update"})
    void reviewTransactionRollbackReleasesToolLockForDeprecation(String operation) {
        Fixture fixture = fixture();
        Long reviewId = operation.equals("update") ? seedReview(fixture) : null;
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            if (operation.equals("create")) {
                reviewService.create(fixture.toolId(), new CreateReviewRequest((short) 5, "rolled back"),
                        fixture.reviewerId(), false);
            } else {
                reviewService.update(fixture.toolId(), reviewId,
                        new UpdateReviewRequest((short) 2, "rolled back update"), fixture.reviewerId(), false);
            }
            status.setRollbackOnly();
        });

        assertReviewState(fixture.toolId(), reviewId, "original", (short) 3);
        transaction.executeWithoutResult(status -> {
            toolRepository.findForUpdateById(fixture.toolId()).orElseThrow();
            // Apply the publishing state change under the same Tool lock used by Role E's transition.
            jdbcTemplate.update("UPDATE tools SET status = 'DEPRECATED' WHERE id = ?", fixture.toolId());
        });

        assertEquals("DEPRECATED", statusOf(fixture.toolId()));
        assertReviewState(fixture.toolId(), reviewId, "original", (short) 3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update"})
    void deprecationTransactionRollbackRestoresPublishedStateForReview(String operation) {
        Fixture fixture = fixture();
        Long reviewId = operation.equals("update") ? seedReview(fixture) : null;
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            toolRepository.findForUpdateById(fixture.toolId()).orElseThrow();
            // Apply the publishing state change under the same Tool lock used by Role E's transition.
            jdbcTemplate.update("UPDATE tools SET status = 'DEPRECATED' WHERE id = ?", fixture.toolId());
            status.setRollbackOnly();
        });

        assertEquals("PUBLISHED", statusOf(fixture.toolId()));
        if (operation.equals("create")) {
            assertNotNull(reviewService.create(fixture.toolId(),
                    new CreateReviewRequest((short) 4, "after rollback"), fixture.reviewerId(), false).id());
        } else {
            reviewService.update(fixture.toolId(), reviewId,
                    new UpdateReviewRequest((short) 4, "after rollback"), fixture.reviewerId(), false);
        }
        assertReviewState(fixture.toolId(), reviewId, "after rollback", (short) 4);
        assertEquals("PUBLISHED", statusOf(fixture.toolId()));
    }

    private Fixture fixture() {
        assertDisposablePostgres();
        String suffix = UUID.randomUUID().toString();
        User owner = userRepository.saveAndFlush(new User("owner-" + suffix + "@example.test", "encoded"));
        User reviewer = userRepository.saveAndFlush(new User("reviewer-" + suffix + "@example.test", "encoded"));
        Category category = categoryRepository.saveAndFlush(new Category("Category " + suffix, "category-" + suffix, null));
        Tool tool = toolRepository.saveAndFlush(new Tool(owner.getId(), category,
                "Tool " + suffix, "tool-" + suffix, "Short", "Description", null));
        jdbcTemplate.update("UPDATE tools SET status = 'PUBLISHED' WHERE id = ?", tool.getId());
        return new Fixture(tool.getId(), reviewer.getId());
    }

    private String statusOf(Long toolId) {
        return jdbcTemplate.queryForObject("SELECT status FROM tools WHERE id = ?", String.class, toolId);
    }

    private long reviewCount(Long toolId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM reviews WHERE tool_id = ?", Long.class, toolId);
    }

    private Long seedReview(Fixture fixture) {
        User reviewer = userRepository.findById(fixture.reviewerId()).orElseThrow();
        Tool tool = toolRepository.findById(fixture.toolId()).orElseThrow();
        return reviewRepository.saveAndFlush(new Review(reviewer, tool, (short) 3, "original")).getId();
    }

    private void assertReviewState(Long toolId, Long reviewId, String comment, short rating) {
        if (reviewId == null && comment.equals("original")) {
            assertEquals(0L, reviewCount(toolId));
            return;
        }
        if (reviewId == null) {
            assertEquals(1L, reviewCount(toolId));
        }
        Review review = reviewId == null
                ? reviewRepository.findByTool_IdOrderByCreatedAtDescIdAsc(toolId, PageRequest.of(0, 1))
                        .getContent().stream().findFirst().orElseThrow()
                : reviewRepository.findByTool_IdAndId(toolId, reviewId).orElseThrow();
        assertEquals(comment, review.getComment());
        assertEquals(rating, review.getRating());
    }

    private void assertDisposablePostgres() {
        try (var connection = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            assertTrue(connection.getCatalog().startsWith("primeskill_test_"),
                    "Rollback regressions may only write to a disposable primeskill_test_* database");
            String url = connection.getMetaData().getURL();
            assertTrue(url.matches(
                            "(?i)^jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/primeskill_test_[a-z0-9_-]+(?:\\?.*)?$"),
                    "Rollback regressions require an explicit loopback PostgreSQL URL");
        } catch (java.sql.SQLException exception) {
            throw new AssertionError("Could not verify disposable PostgreSQL test database", exception);
        }
    }

    private record Fixture(Long toolId, Long reviewerId) { }
}
