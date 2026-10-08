package com.example.toolhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.toolhub.domain.entity.Category;
import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.entity.User;
import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.repository.CategoryRepository;
import com.example.toolhub.repository.ReviewRepository;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Checks that either side of the shared Tool lock can proceed after the other transaction rolls back. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:review-lock-rollback;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema.sql"
})
class ReviewServiceLockRollbackIntegrationTest {

    @Autowired private ReviewService reviewService;
    @Autowired private ToolRepository toolRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void reviewTransactionRollbackReleasesToolLockForDeprecation() {
        Fixture fixture = fixture();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            reviewService.create(fixture.toolId(), new CreateReviewRequest((short) 5, "rolled back"),
                    fixture.reviewerId(), false);
            status.setRollbackOnly();
        });

        assertEquals(0L, reviewCount(fixture.toolId()));
        transaction.executeWithoutResult(status -> {
            toolRepository.findForUpdateById(fixture.toolId()).orElseThrow();
            jdbcTemplate.update("UPDATE tools SET status = 'DEPRECATED' WHERE id = ?", fixture.toolId());
        });

        assertEquals("DEPRECATED", statusOf(fixture.toolId()));
        assertEquals(0L, reviewCount(fixture.toolId()));
    }

    @Test
    void deprecationTransactionRollbackRestoresPublishedStateForReview() {
        Fixture fixture = fixture();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            toolRepository.findForUpdateById(fixture.toolId()).orElseThrow();
            jdbcTemplate.update("UPDATE tools SET status = 'DEPRECATED' WHERE id = ?", fixture.toolId());
            status.setRollbackOnly();
        });

        assertEquals("PUBLISHED", statusOf(fixture.toolId()));
        assertNotNull(reviewService.create(fixture.toolId(), new CreateReviewRequest((short) 4, "after rollback"),
                fixture.reviewerId(), false).id());
        assertEquals(1L, reviewCount(fixture.toolId()));
        assertEquals("PUBLISHED", statusOf(fixture.toolId()));
    }

    private Fixture fixture() {
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

    private record Fixture(Long toolId, Long reviewerId) { }
}
