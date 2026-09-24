package com.example.toolhub.controller.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.toolhub.dto.response.CategoryResponse;
import com.example.toolhub.dto.response.TagResponse;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.service.CategoryService;
import com.example.toolhub.service.TagService;
import com.example.toolhub.service.ToolSearchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.ui.ExtendedModelMap;

@ExtendWith(MockitoExtension.class)
class ToolBrowseWebControllerTest {
    @Mock private ToolSearchService toolSearchService;
    @Mock private CategoryService categoryService;
    @Mock private TagService tagService;

    private ToolBrowseWebController controller;

    @BeforeEach
    void setUp() {
        controller = new ToolBrowseWebController(toolSearchService, categoryService, tagService);
    }

    @Test
    void browse_exposesSearchResultAndFilters() {
        var pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
        var resultPage = new PageImpl<>(List.of(ToolResponse.builder().id(1L).name("Calendar").build()),
                pageable, 1);
        when(toolSearchService.search(any(), any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(resultPage);
        when(categoryService.findAll())
                .thenReturn(List.of(new CategoryResponse(1L, "Productivity", "productivity", null)));
        when(tagService.findAll()).thenReturn(List.of(new TagResponse(1L, "Calendar", "calendar")));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.browse("cal", 1L, "calendar", "newest", 0, 20, model);

        assertEquals("tools/list", view);
        assertEquals(1, ((List<?>) model.get("tools")).size());
        assertEquals(1L, model.get("totalElements"));
        assertEquals("explore", model.get("activeNav"));
        assertEquals("สำรวจเครื่องมือ", model.get("pageTitle"));
        verify(toolSearchService).search(any(), any(), any(), anyString(), anyInt(), anyInt());
    }

    @Test
    void browse_whenPageNegative_clampsToFirstPage() {
        var pageable = PageRequest.of(0, 20);
        when(toolSearchService.search(any(), any(), anyList(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<ToolResponse>(List.of(), pageable, 0));
        when(categoryService.findAll()).thenReturn(List.of());
        when(tagService.findAll()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        controller.browse(null, null, null, "newest", -5, 20, model);

        org.mockito.ArgumentCaptor<Integer> pageCaptor = org.mockito.ArgumentCaptor.forClass(Integer.class);
        verify(toolSearchService).search(any(), any(), anyList(), anyString(), pageCaptor.capture(), anyInt());
        assertEquals(0, pageCaptor.getValue());
    }
}
