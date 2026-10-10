package com.example.toolhub;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.toolhub.domain.entity.*;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.dto.request.CreateReviewRequest;
import com.example.toolhub.dto.request.UpdateReviewRequest;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.repository.*;
import com.example.toolhub.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RatingBrowseIntegrationTest extends RatingBrowseContract {}

/** Runs unchanged against H2 and guarded disposable PostgreSQL. */
@Transactional
abstract class RatingBrowseContract {
    @Autowired ToolSearchService search;
    @Autowired ReviewSummaryService summaries;
    @Autowired ReviewService reviews;
    @Autowired UserRepository users;
    @Autowired ToolRepository tools;
    @Autowired CategoryRepository categories;
    @Autowired TagRepository tags;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory factory;
    @Autowired MockMvc mvc;
    long owner, author, secondAuthor, category, high, tied, low, unrated;
    String suffix;

    @BeforeEach void seedRatings() {
        suffix = UUID.randomUUID().toString();
        owner = users.saveAndFlush(new User("owner-" + suffix + "@test.invalid", "unused")).getId();
        author = users.saveAndFlush(new User("author-" + suffix + "@test.invalid", "unused")).getId();
        secondAuthor = users.saveAndFlush(new User("other-" + suffix + "@test.invalid", "unused")).getId();
        category = categories.saveAndFlush(new Category("Rating " + suffix, suffix, null)).getId();
        high = tool("High", category, ToolStatus.PUBLISHED);
        tied = tool("Tied", category, ToolStatus.PUBLISHED);
        low = tool("Low", category, ToolStatus.PUBLISHED);
        unrated = tool("Unrated", category, ToolStatus.PUBLISHED);
        review(high, author, 5); review(high, secondAuthor, 4);
        review(tied, author, 5); review(tied, secondAuthor, 4);
        review(low, author, 3);
        em.clear();
    }

    long tool(String name, long categoryId, ToolStatus status) {
        Tool entity = new Tool(owner, categories.getReferenceById(categoryId), name + " " + suffix,
                name.toLowerCase(Locale.ROOT).replace("%", "percent").replace("_", "dash") + "-" + UUID.randomUUID(),
                "Short " + suffix, "Details " + suffix, null);
        entity.changeStatus(status);
        return tools.saveAndFlush(entity).getId();
    }

    void review(long toolId, long userId, int score) {
        jdbc.update("INSERT INTO reviews(tool_id,user_id,rating) VALUES(?,?,?)", toolId, userId, score);
    }

    Page<ToolResponse> page(int number, int size) { return search.search(suffix, null, null, "rating", number, size); }
    List<Long> ids(Page<ToolResponse> result) { return result.map(ToolResponse::getId).getContent(); }

    @Test void ordersFullPrecisionAverageThenIdAndPutsUnratedLastBeforePagination() {
        assertEquals(List.of(high, tied), ids(page(0, 2)));
        assertEquals(List.of(low, unrated), ids(page(1, 2)));
        assertEquals(4, page(0, 2).getTotalElements());
        assertEquals(2, page(0, 2).getTotalPages());
        // Different averages that both display as 4.5 must not be rounded for sorting.
        long precise = tool("Precise", category, ToolStatus.PUBLISHED);
        for (int i = 0; i < 21; i++) {
            long userId = users.saveAndFlush(new User("precision-" + i + "-" + suffix + "@test.invalid", "unused")).getId();
            review(precise, userId, i < 11 ? 5 : 4);
        }
        em.clear();
        assertEquals(List.of(precise, high), ids(page(0, 2)));
    }

    @Test void combinesAnyTagsCategoryAndKeywordWithoutDuplicateToolsOrCounts() {
        long firstTag = tags.saveAndFlush(new Tag("First " + suffix, "first-" + suffix)).getId();
        long secondTag = tags.saveAndFlush(new Tag("Second " + suffix, "second-" + suffix)).getId();
        for (long tag : List.of(firstTag, secondTag)) jdbc.update("INSERT INTO tool_tags(tool_id,tag_id) VALUES(?,?)", high, tag);
        jdbc.update("INSERT INTO tool_tags(tool_id,tag_id) VALUES(?,?)", low, firstTag);
        var result = search.search(suffix, category, List.of("first-" + suffix, "second-" + suffix), "rating", 0, 1);
        assertEquals(List.of(high), ids(result));
        assertEquals(2, result.getTotalElements());
        assertEquals(List.of(low), ids(search.search(suffix, category,
                List.of("first-" + suffix, "second-" + suffix), "rating", 1, 1)));
        assertTrue(search.search(suffix, category + 1000000, null, "rating", 0, 20).isEmpty());
    }

    @Test void ratingWithTagsAndNoKeywordWorksAndUnknownTagsReturnEmpty() {
        Tag tag = tags.saveAndFlush(new Tag("Only " + suffix, "only-" + suffix));
        jdbc.update("INSERT INTO tool_tags(tool_id,tag_id) VALUES(?,?)", unrated, tag.getId());
        assertEquals(List.of(unrated), ids(search.search(null, null, List.of(tag.getSlug()), "rating", 0, 20)));
        assertTrue(search.search(null, null, List.of("missing-" + suffix), "rating", 0, 20).isEmpty());
    }

    @Test void missingKeywordWorksForAllSortsWithAndWithoutTags() {
        Tag tag = tags.saveAndFlush(new Tag("All sorts " + suffix, "all-sorts-" + suffix));
        jdbc.update("INSERT INTO tool_tags(tool_id,tag_id) VALUES(?,?)", high, tag.getId());
        em.clear();
        for (String sort : List.of("newest", "popular", "relevance", "rating")) {
            assertEquals(4, search.search(null, category, null, sort, 0, 2).getTotalElements(), sort);
            assertEquals(List.of(high), ids(search.search("  ", null, List.of(tag.getSlug()), sort, 0, 20)), sort);
        }
    }

    @Test void hiddenToolsWithHighRatingsNeverAppear() {
        for (ToolStatus state : List.of(ToolStatus.DRAFT, ToolStatus.PENDING, ToolStatus.DEPRECATED)) {
            long hidden = tool("Hidden" + state, category, state);
            review(hidden, author, 5);
        }
        assertEquals(List.of(high, tied, low, unrated), ids(page(0, 20)));
        assertEquals(4, page(0, 20).getTotalElements());
    }

    @Test void keywordWildcardsAreLiteralForRatingQueries() {
        long literal = tool("100%_done", category, ToolStatus.PUBLISHED);
        tool("100XXdone", category, ToolStatus.PUBLISHED);
        assertEquals(List.of(literal), ids(search.search("100%_done", category, null, "rating", 0, 20)));
    }

    @Test void nextBrowseReflectsReviewCreateUpdateAndDelete() {
        var created = reviews.create(unrated, new CreateReviewRequest((short) 5, "New"), author, false);
        em.flush(); em.clear();
        assertEquals(unrated, ids(page(0, 1)).get(0));
        assertEquals(5.0, summaries.summarizeByToolIds(List.of(unrated)).get(unrated).avgRating());
        reviews.update(unrated, created.id(), new UpdateReviewRequest((short) 1, "Edited"), author, false);
        em.flush(); em.clear();
        assertEquals(List.of(high, tied, low, unrated), ids(page(0, 20)));
        assertEquals(1.0, summaries.summarizeByToolIds(List.of(unrated)).get(unrated).avgRating());
        reviews.delete(unrated, created.id(), author, false);
        em.flush(); em.clear();
        var empty = summaries.summarizeByToolIds(List.of(unrated)).get(unrated);
        assertNull(empty.avgRating()); assertEquals(0, empty.reviewCount());
    }

    @Test void joinedPageCountAndBatchSummaryUseAtMostThreeQueriesForManyCategories() {
        for (int i = 0; i < 12; i++) {
            long categoryId = categories.saveAndFlush(new Category("Other " + i + suffix, "other-" + i + suffix, null)).getId();
            tool("Extra" + i, categoryId, ToolStatus.PUBLISHED);
        }
        em.clear();
        var stats = factory.unwrap(SessionFactory.class).getStatistics();
        boolean previous = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true); stats.clear();
        try {
            var result = page(0, 10);
            assertEquals(10, result.getNumberOfElements()); assertEquals(16, result.getTotalElements());
            var scores = summaries.summarizeByToolIds(ids(result));
            assertEquals(10, scores.size());
            result.forEach(tool -> assertNotNull(tool.getCategoryName()));
            assertTrue(stats.getPrepareStatementCount() <= 3, "Page + count + batch; got " + stats.getPrepareStatementCount());
            assertEquals(0, stats.getEntityFetchCount(), "Category must be fetched in the page query");
        } finally { stats.setStatisticsEnabled(previous); }
    }

    @Test void browseRendersRealScoresAndEmptyStateAndApiKeepsRatingOrder() throws Exception {
        mvc.perform(get("/tools").param("q", suffix).param("sort", "rating"))
                .andExpect(status().isOk()).andExpect(view().name("tools/list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">4.5</strong>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2 reviews")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No reviews yet")));
        mvc.perform(get("/api/v1/tools").param("q", suffix).param("sort", "rating").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(high))
                .andExpect(jsonPath("$.content[1].id").value(tied)).andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/tools").param("q", "missing-" + suffix).param("sort", "rating"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("No tools found")));
    }
    @Test void homeRendersOnlyPublishedToolsWithBoundedQueries() throws Exception {
        for (int i = 0; i < 10; i++) tool("Public " + i, category, ToolStatus.PUBLISHED);
        tool("PrivateDraft", category, ToolStatus.DRAFT);
        tool("PrivatePending", category, ToolStatus.PENDING);
        tool("PrivateDeprecated", category, ToolStatus.DEPRECATED);
        em.clear();
        var stats = factory.unwrap(SessionFactory.class).getStatistics();
        boolean previous = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true); stats.clear();
        try {
            mvc.perform(get("/"))
                    .andExpect(status().isOk()).andExpect(view().name("home"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Popular tools")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PrivateDraft"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PrivatePending"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PrivateDeprecated"))));
            assertTrue(stats.getPrepareStatementCount() <= 6,
                    "Two pages + their counts + categories + one aggregate; got " + stats.getPrepareStatementCount());
            assertEquals(0, stats.getEntityFetchCount());
        } finally { stats.setStatisticsEnabled(previous); }
    }

    @Test void redesignAssetsAreAvailableWithoutAuthentication() throws Exception {
        for (String asset : List.of("/css/primeskill.css", "/css/fonts.css", "/js/theme-init.js", "/js/ui.js", "/img/ui-icons.svg", "/fonts/primeskill-0.woff2")) {
            mvc.perform(get(asset)).andExpect(status().isOk());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head(asset)).andExpect(status().isOk());
        }
        mvc.perform(get("/dashboard/tools")).andExpect(status().isUnauthorized());
    }
}
