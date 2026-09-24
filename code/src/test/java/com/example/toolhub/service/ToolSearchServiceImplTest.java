package com.example.toolhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.mapper.ToolMapper;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.service.impl.ToolSearchServiceImpl;
import com.example.toolhub.service.search.NewestToolSortStrategy;
import com.example.toolhub.service.search.PopularityToolSortStrategy;
import com.example.toolhub.service.search.RatingToolSortStrategy;
import com.example.toolhub.service.search.RelevanceToolSortStrategy;
import com.example.toolhub.service.search.ToolSortStrategy;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ToolSearchServiceImplTest {
    @Mock private ToolRepository toolRepository;

    private ToolSearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        List<ToolSortStrategy> strategies = List.of(
                new NewestToolSortStrategy(),
                new PopularityToolSortStrategy(),
                new RatingToolSortStrategy(),
                new RelevanceToolSortStrategy());
        searchService = new ToolSearchServiceImpl(toolRepository, new ToolMapper(), strategies);
    }

    @Test
    void search_alwaysFiltersPublishedStatus() {
        when(toolRepository.searchPublished(eq(ToolStatus.PUBLISHED), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        searchService.search("calendar", null, null, "newest", 0, 20);

        verify(toolRepository).searchPublished(eq(ToolStatus.PUBLISHED), any(), any(), any());
    }

    @Test
    void search_whenTagsProvided_usesTagQuery() {
        when(toolRepository.searchPublishedWithTags(eq(ToolStatus.PUBLISHED), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        searchService.search(null, null, List.of("calendar"), "newest", 0, 20);

        verify(toolRepository).searchPublishedWithTags(eq(ToolStatus.PUBLISHED), any(), any(),
                eq(List.of("calendar")), any());
    }

    @Test
    void search_escapesLikeWildcards() {
        when(toolRepository.searchPublished(eq(ToolStatus.PUBLISHED), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);

        searchService.search("100%_done", null, null, "newest", 0, 20);

        verify(toolRepository).searchPublished(eq(ToolStatus.PUBLISHED), any(),
                keywordCaptor.capture(), any());
        assertEquals("100\\%\\_done", keywordCaptor.getValue());
    }

    @Test
    void search_whenSortUnsupported_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> searchService.search(null, null, null, "random", 0, 20));
    }

    @Test
    void search_whenPageNegative_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> searchService.search(null, null, null, "newest", -1, 20));
    }

    @Test
    void search_whenSizeTooLarge_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> searchService.search(null, null, null, "newest", 0, 101));
    }

    @Test
    void search_relevanceWithoutKeyword_fallsBackToNewest() {
        when(toolRepository.searchPublished(eq(ToolStatus.PUBLISHED), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        searchService.search(null, null, null, "relevance", 0, 20);

        verify(toolRepository).searchPublished(eq(ToolStatus.PUBLISHED), any(), any(),
                pageableCaptor.capture());
        assertEquals("createdAt",
                pageableCaptor.getValue().getSort().iterator().next().getProperty());
    }
}
