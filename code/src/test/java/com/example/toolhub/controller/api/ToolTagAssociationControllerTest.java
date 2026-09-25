package com.example.toolhub.controller.api;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.toolhub.dto.response.TagResponse;
import com.example.toolhub.exception.GlobalExceptionHandler;
import com.example.toolhub.exception.ResourceNotFoundException;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.TagService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ToolTagAssociationControllerTest {
    private MockMvc mockMvc;
    private TagService tagService;
    private CurrentActorProvider currentActorProvider;

    @BeforeEach
    void setUp() {
        tagService = mock(TagService.class);
        currentActorProvider = mock(CurrentActorProvider.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ToolTagAssociationController(tagService, currentActorProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void findTagsOfTool_whenPublished_returnsTags() throws Exception {
        when(currentActorProvider.currentActor()).thenReturn(new CurrentActor(null, false));
        when(tagService.findTagsOfTool(anyLong(), isNull(), anyBoolean()))
                .thenReturn(List.of(new TagResponse(1L, "Calendar", "calendar")));

        mockMvc.perform(get("/api/v1/tools/1/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("calendar"));
    }

    @Test
    void findTagsOfTool_whenNonPublishedAnonymous_returnsNotFound() throws Exception {
        when(currentActorProvider.currentActor()).thenReturn(new CurrentActor(null, false));
        when(tagService.findTagsOfTool(anyLong(), isNull(), anyBoolean()))
                .thenThrow(new ResourceNotFoundException("Tool not found: 1"));

        mockMvc.perform(get("/api/v1/tools/1/tags"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
