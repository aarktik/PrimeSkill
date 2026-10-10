package com.example.toolhub.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.toolhub.dto.response.ToolVersionResponse;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.GlobalExceptionHandler;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.ToolVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ToolVersionRestControllerTest {
    private MockMvc mockMvc;
    private ToolVersionService service;
    private CurrentActorProvider actorProvider;

    @BeforeEach
    void setUp() {
        service = mock(ToolVersionService.class);
        actorProvider = mock(CurrentActorProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ToolVersionRestController(service, actorProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void invalidVersionReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/tools/1/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void duplicateVersionReturnsConflict() throws Exception {
        CurrentActor actor = new CurrentActor(7L, false);
        when(actorProvider.requireActor()).thenReturn(actor);
        when(service.create(eq(1L), any(), eq(actor)))
                .thenThrow(new CatalogConflictException("Tool version already exists"));

        mockMvc.perform(post("/api/v1/tools/1/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    @Test
    void createReturnsLocation() throws Exception {
        CurrentActor actor = new CurrentActor(7L, false);
        when(actorProvider.requireActor()).thenReturn(actor);
        when(service.create(eq(1L), any(), eq(actor)))
                .thenReturn(new ToolVersionResponse(9L, 1L, "1.0.0", null, null, null, null));

        mockMvc.perform(post("/api/v1/tools/1/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Location", "/api/v1/tools/1/versions/9"));
    }
}
