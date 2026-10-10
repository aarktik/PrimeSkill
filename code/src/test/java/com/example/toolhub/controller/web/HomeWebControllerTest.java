package com.example.toolhub.controller.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.dto.response.ReviewSummary;
import com.example.toolhub.service.CategoryService;
import com.example.toolhub.service.ReviewSummaryService;
import com.example.toolhub.service.ToolSearchService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.ui.ExtendedModelMap;

class HomeWebControllerTest {
    @Test void sharesOneDeduplicatedSummaryBatchAcrossBothSections() {
        var search = mock(ToolSearchService.class);
        var categories = mock(CategoryService.class);
        var summaries = mock(ReviewSummaryService.class);
        var first = ToolResponse.builder().id(1L).name("First").build();
        var second = ToolResponse.builder().id(2L).name("Second").build();
        when(search.search(null,null,null,"popular",0,6)).thenReturn(new PageImpl<>(List.of(first)));
        when(search.search(null,null,null,"newest",0,6)).thenReturn(new PageImpl<>(List.of(first,second)));
        var aggregate = Map.of(1L,new ReviewSummary(1L,4.5,2));
        when(summaries.summarizeByToolIds(List.of(1L,2L))).thenReturn(aggregate);
        var model = new ExtendedModelMap();
        assertEquals("home",new HomeWebController(search,categories,summaries).home(model));
        assertSame(aggregate,model.get("reviewSummaries"));
        verify(summaries).summarizeByToolIds(List.of(1L,2L));
        verifyNoMoreInteractions(summaries);
    }
    @Test void emptyDirectoryDoesNotRequestAnAggregateOrInventScores() {
        var search = mock(ToolSearchService.class);
        var categories = mock(CategoryService.class);
        var summaries = mock(ReviewSummaryService.class);
        when(search.search(null,null,null,"popular",0,6)).thenReturn(new PageImpl<>(List.of()));
        when(search.search(null,null,null,"newest",0,6)).thenReturn(new PageImpl<>(List.of()));
        var model = new ExtendedModelMap();
        new HomeWebController(search,categories,summaries).home(model);
        assertEquals(Map.of(),model.get("reviewSummaries"));
        assertEquals(List.of(),model.get("popularTools"));
        verifyNoInteractions(summaries);
    }
}
