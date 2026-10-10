package com.example.toolhub.controller.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.toolhub.domain.enums.PublishingAction;
import com.example.toolhub.exception.GlobalExceptionHandler;
import com.example.toolhub.exception.InvalidStateTransitionException;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.PublishingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublishingRestControllerTest {
    private MockMvc mockMvc;
    private PublishingService service;
    private CurrentActorProvider actorProvider;

    @BeforeEach
    void setUp() {
        service = mock(PublishingService.class);
        actorProvider = mock(CurrentActorProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PublishingRestController(service, actorProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void invalidTransitionReturnsConflictCode() throws Exception {
        CurrentActor actor = new CurrentActor(7L, false);
        when(actorProvider.requireActor()).thenReturn(actor);
        when(service.transition(1L, PublishingAction.SUBMIT, actor))
                .thenThrow(new InvalidStateTransitionException("Cannot submit a pending tool"));

        mockMvc.perform(post("/api/v1/tools/1/submit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }
}
