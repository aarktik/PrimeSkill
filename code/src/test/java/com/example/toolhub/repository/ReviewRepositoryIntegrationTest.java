package com.example.toolhub.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.toolhub.domain.entity.Category;
import com.example.toolhub.domain.entity.Review;
import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.entity.User;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.persistence.EntityManager;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:review-repository;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema.sql"
})
class ReviewRepositoryIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private ReviewRepository reviewRepository;

    @Test
    void summaryQueryAggregatesRatingsAndLeavesUnratedToolWithoutRow() {
        Fixture fixture = fixture();
        User secondReviewer = persistUser("second@example.com");
        Tool unratedTool = persistTool(fixture.owner(), fixture.category(), "unrated-tool");
        entityManager.persist(new Review(fixture.owner(), fixture.tool(), (short) 4, "first"));
        entityManager.persist(new Review(secondReviewer, fixture.tool(), (short) 5, "second"));
        entityManager.flush();

        List<ReviewSummaryProjection> summaries = reviewRepository.summarizeByToolIds(
                Set.of(fixture.tool().getId(), unratedTool.getId()));

        assertEquals(1, summaries.size());
        assertEquals(fixture.tool().getId(), summaries.get(0).getToolId());
        assertEquals(4.5, summaries.get(0).getAvgRating(), 0.0001);
        assertEquals(2L, summaries.get(0).getReviewCount());
    }

    @Test
    void uniqueConstraintRejectsSecondReviewFromSameUserForTool() {
        Fixture fixture = fixture();
        reviewRepository.saveAndFlush(new Review(fixture.owner(), fixture.tool(), (short) 4, null));

        assertThrows(DataIntegrityViolationException.class,
                () -> reviewRepository.saveAndFlush(new Review(fixture.owner(), fixture.tool(), (short) 5, null)));
    }

    @Test
    void ratingCheckRejectsValuesOutsideOneToFive() {
        Fixture fixture = fixture();

        assertThrows(DataIntegrityViolationException.class,
                () -> reviewRepository.saveAndFlush(new Review(fixture.owner(), fixture.tool(), (short) 0, null)));
    }

    @Test
    void commentLengthCheckRejectsMoreThanTwoThousandCharacters() {
        Fixture fixture = fixture();

        assertThrows(DataIntegrityViolationException.class,
                () -> reviewRepository.saveAndFlush(
                        new Review(fixture.owner(), fixture.tool(), (short) 5, "x".repeat(2001))));
    }

    private Fixture fixture() {
        User owner = persistUser("owner@example.com");
        Category category = new Category("Testing", "testing", null);
        entityManager.persist(category);
        Tool tool = persistTool(owner, category, "review-tool");
        entityManager.flush();
        return new Fixture(owner, category, tool);
    }

    private User persistUser(String email) {
        User user = new User(email, "encoded-password");
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Tool persistTool(User owner, Category category, String slug) {
        Tool tool = new Tool(owner.getId(), category, slug, slug, "short", "description", null);
        entityManager.persist(tool);
        return tool;
    }

    private record Fixture(User owner, Category category, Tool tool) { }
}
