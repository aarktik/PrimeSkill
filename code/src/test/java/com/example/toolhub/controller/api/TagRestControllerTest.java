package com.example.toolhub.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.toolhub.dto.response.TagResponse;
import com.example.toolhub.exception.AuthenticationRequiredException;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.GlobalExceptionHandler;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.TagService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TagRestControllerTest {
    private MockMvc mockMvc;
    private TagService tagService;
    private CurrentActorProvider currentActorProvider;

    @BeforeEach
    void setUp() {
        tagService = mock(TagService.class);
        currentActorProvider = mock(CurrentActorProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TagRestController(tagService, currentActorProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void findAll_returnsTagList() throws Exception {
        when(tagService.findAll()).thenReturn(List.of(new TagResponse(1L, "Calendar", "calendar")));

        mockMvc.perform(get("/api/v1/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("calendar"));
    }

    @Test
    void create_whenSlugConflicts_returnsConflict() throws Exception {
        when(currentActorProvider.requireActor()).thenReturn(new CurrentActor(1L, true));
        when(tagService.create(any(), anyBoolean()))
                .thenThrow(new CatalogConflictException("Tag name or slug already exists"));

        mockMvc.perform(post("/api/v1/admin/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Calendar\",\"slug\":\"calendar\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    @Test
    void create_whenUnauthenticated_returnsUnauthorized() throws Exception {
        when(currentActorProvider.requireActor()).thenThrow(new AuthenticationRequiredException());

        mockMvc.perform(post("/api/v1/admin/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Calendar\",\"slug\":\"calendar\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void create_whenSlugIsInvalid_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/admin/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Calendar\",\"slug\":\"INVALID SLUG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
