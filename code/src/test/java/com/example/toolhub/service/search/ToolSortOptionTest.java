package com.example.toolhub.service.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToolSortOptionTest {

    @Test
    void from_supportsAllDocumentedValues() {
        assertEquals(ToolSortOption.NEWEST, ToolSortOption.from("newest"));
        assertEquals(ToolSortOption.POPULAR, ToolSortOption.from("popular"));
        assertEquals(ToolSortOption.RATING, ToolSortOption.from("rating"));
        assertEquals(ToolSortOption.RELEVANCE, ToolSortOption.from("relevance"));
    }

    @Test
    void from_isCaseInsensitiveAndTrimmed() {
        assertEquals(ToolSortOption.NEWEST, ToolSortOption.from("  Newest "));
    }

    @Test
    void from_whenUnsupported_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> ToolSortOption.from("random"));
    }

    @Test
    void strategies_coverAllOptionsWithStableTieBreak() {
        var strategies = java.util.List.<ToolSortStrategy>of(
                new NewestToolSortStrategy(),
                new PopularityToolSortStrategy(),
                new RatingToolSortStrategy(),
                new RelevanceToolSortStrategy());
        for (ToolSortOption option : ToolSortOption.values()) {
            var sort = strategies.stream()
                    .filter(strategy -> strategy.option() == option)
                    .findFirst()
                    .orElseThrow()
                    .toSort();
            assertTrue(sort.getOrderFor("id") != null, "missing stable id tie-break for " + option);
        }
    }

    @Test
    void newest_sortsByCreatedAtDesc() {
        var sort = new NewestToolSortStrategy().toSort();
        assertEquals(org.springframework.data.domain.Sort.Direction.DESC,
                sort.getOrderFor("createdAt").getDirection());
    }

    @Test
    void popular_sortsByViewCountDesc() {
        var sort = new PopularityToolSortStrategy().toSort();
        assertEquals(org.springframework.data.domain.Sort.Direction.DESC,
                sort.getOrderFor("viewCount").getDirection());
    }
}
