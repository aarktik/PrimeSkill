package com.example.toolhub.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.exception.GlobalExceptionHandler;
import com.example.toolhub.service.ToolSearchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ToolSearchRestControllerTest {
    private MockMvc mockMvc;
    private ToolSearchService toolSearchService;

    @BeforeEach
    void setUp() {
        toolSearchService = mock(ToolSearchService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ToolSearchRestController(toolSearchService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void search_returnsPagedResponseShape() throws Exception {
        var page = new PageImpl<>(List.of(ToolResponse.builder().id(1L).name("Calendar").build()),
                PageRequest.of(0, 20, Sort.by("createdAt").descending()), 1);
        when(toolSearchService.search(any(), any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(page.map(tool -> tool));

        mockMvc.perform(get("/api/v1/tools").param("q", "cal").param("sort", "newest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void search_whenSortUnsupported_returnsBadRequest() throws Exception {
        when(toolSearchService.search(any(), any(), any(), anyString(), anyInt(), anyInt()))
                .thenThrow(new IllegalArgumentException("Unsupported sort: random"));

        mockMvc.perform(get("/api/v1/tools").param("sort", "random"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
