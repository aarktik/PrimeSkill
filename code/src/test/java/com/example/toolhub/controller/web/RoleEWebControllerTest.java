package com.example.toolhub.controller.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.exception.InvalidStateTransitionException;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.PublishingService;
import com.example.toolhub.service.ToolService;
import com.example.toolhub.service.ToolVersionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class RoleEWebControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ToolService toolService;
    @MockitoBean private ToolVersionService versionService;
    @MockitoBean private PublishingService publishingService;
    @MockitoBean private CurrentActorProvider actorProvider;

    @Test
    void versionPageRendersOwnerActions() throws Exception {
        CurrentActor actor = new CurrentActor(7L, false);
        when(actorProvider.requireActor()).thenReturn(actor);
        when(toolService.getByIdOrSlug("1", 7L, false)).thenReturn(tool(ToolStatus.DRAFT));
        when(versionService.list(1L, actor)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/tools/1/versions"))
                .andExpect(status().isOk())
                .andExpect(view().name("versions/list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("New version")));
    }

    @Test
    void versionFormKeepsValidationErrors() throws Exception {
        CurrentActor actor = new CurrentActor(7L, false);
        when(actorProvider.requireActor()).thenReturn(actor);
        when(toolService.getByIdOrSlug("1", 7L, false)).thenReturn(tool(ToolStatus.DRAFT));

        mockMvc.perform(post("/dashboard/tools/1/versions").param("version", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("versions/form"))
                .andExpect(model().attributeHasFieldErrors("versionRequest", "version"));
    }

    @Test
    void moderationPageRendersQueue() throws Exception {
        CurrentActor actor = new CurrentActor(8L, true);
        when(actorProvider.requireActor()).thenReturn(actor);
        when(publishingService.listPending(any(), eq(actor))).thenReturn(Page.empty());

        mockMvc.perform(get("/admin/tools"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/moderation"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("The review queue is clear.")));
    }

    @Test
    void publicVersionPageRenders() throws Exception {
        CurrentActor actor = new CurrentActor(null, false);
        when(actorProvider.currentActor()).thenReturn(actor);
        when(toolService.getByIdOrSlug("tool", null, false)).thenReturn(tool(ToolStatus.PUBLISHED));
        when(versionService.list(1L, actor)).thenReturn(List.of());

        mockMvc.perform(get("/tools/tool/versions"))
                .andExpect(status().isOk())
                .andExpect(view().name("versions/public"));
    }

    @Test
    void nonOwnerCannotOpenDashboardVersions() throws Exception {
        CurrentActor actor = new CurrentActor(8L, false);
        when(actorProvider.requireActor()).thenReturn(actor);
        when(toolService.getByIdOrSlug("1", 8L, false)).thenReturn(tool(ToolStatus.PUBLISHED));

        mockMvc.perform(get("/dashboard/tools/1/versions"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("versions/error"));
    }

    @Test
    void invalidSubmitShowsSafeConflictPage() throws Exception {
        CurrentActor actor = new CurrentActor(7L, false);
        when(actorProvider.requireActor()).thenReturn(actor);
        when(toolService.getByIdOrSlug("1", 7L, false)).thenReturn(tool(ToolStatus.PENDING));
        when(publishingService.transition(1L, com.example.toolhub.domain.enums.PublishingAction.SUBMIT, actor))
                .thenThrow(new InvalidStateTransitionException("internal state details"));

        mockMvc.perform(post("/dashboard/tools/1/submit"))
                .andExpect(status().isConflict())
                .andExpect(view().name("versions/error"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal state details"))));
    }

    @Test
    void cannotOpenVersionFormForPendingTool() throws Exception {
        CurrentActor actor = new CurrentActor(7L, false);
        when(actorProvider.requireActor()).thenReturn(actor);
        when(toolService.getByIdOrSlug("1", 7L, false)).thenReturn(tool(ToolStatus.PENDING));

        mockMvc.perform(get("/dashboard/tools/1/versions/new"))
                .andExpect(status().isConflict())
                .andExpect(view().name("versions/error"));
    }

    private ToolResponse tool(ToolStatus status) {
        return ToolResponse.builder().id(1L).name("Tool").slug("tool")
                .ownerId(7L).status(status).build();
    }
}
