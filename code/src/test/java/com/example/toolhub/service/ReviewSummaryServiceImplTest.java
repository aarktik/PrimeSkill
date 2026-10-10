package com.example.toolhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.toolhub.dto.response.ReviewSummary;
import com.example.toolhub.repository.ReviewRepository;
import com.example.toolhub.repository.ReviewSummaryProjection;
import com.example.toolhub.service.impl.ReviewSummaryServiceImpl;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReviewSummaryServiceImplTest {

    private ReviewRepository reviewRepository;
    private ReviewSummaryServiceImpl service;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        service = new ReviewSummaryServiceImpl(reviewRepository);
    }

    @Test
    void summarizeByToolIds_whenInputIsEmpty_doesNotQueryRepository() {
        assertEquals(Map.of(), service.summarizeByToolIds(List.of()));
        verify(reviewRepository, never()).summarizeByToolIds(
                org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void summarizeByToolIds_batchesResultsAndFillsToolsWithoutReviews() {
        ReviewSummaryProjection ratedTool = mock(ReviewSummaryProjection.class);
        when(ratedTool.getToolId()).thenReturn(11L);
        when(ratedTool.getAvgRating()).thenReturn(4.25);
        when(ratedTool.getReviewCount()).thenReturn(4L);
        when(reviewRepository.summarizeByToolIds(Set.of(11L, 12L))).thenReturn(List.of(ratedTool));

        Map<Long, ReviewSummary> result = service.summarizeByToolIds(List.of(11L, 12L, 11L));

        assertEquals(new ReviewSummary(11L, 4.25, 4), result.get(11L));
        assertEquals(new ReviewSummary(12L, null, 0), result.get(12L));
        verify(reviewRepository).summarizeByToolIds(Set.of(11L, 12L));
    }

    @Test
    void summarizeByToolIds_whenOnlyNullIds_returnsEmptyMapWithoutQuery() {
        assertEquals(Map.of(), service.summarizeByToolIds(Arrays.asList(null, null)));
        verify(reviewRepository, never()).summarizeByToolIds(
                org.mockito.ArgumentMatchers.anyCollection());
    }
}
