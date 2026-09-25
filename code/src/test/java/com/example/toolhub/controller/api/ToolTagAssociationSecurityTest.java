package com.example.toolhub.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.toolhub.config.PasswordConfig;
import com.example.toolhub.config.SecurityConfig;
import com.example.toolhub.dto.response.TagResponse;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.security.JpaUserDetailsService;
import com.example.toolhub.service.TagService;
import com.example.toolhub.service.ToolSearchService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
        TagRestController.class,
        ToolSearchRestController.class,
        ToolTagAssociationController.class
})
@Import({
        SecurityConfig.class,
        PasswordConfig.class,
        com.example.toolhub.security.RestSecurityExceptionHandler.class
})
class ToolTagAssociationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TagService tagService;

    @MockitoBean
    private ToolSearchService toolSearchService;

    @MockitoBean
    private CurrentActorProvider currentActorProvider;

    @MockitoBean
    private JpaUserDetailsService userDetailsService;

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void anonymous_canSearchPublishedTools() throws Exception {
        when(toolSearchService.search(any(), any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20, Sort.by("createdAt").descending()), 0));

        mockMvc.perform(get("/api/v1/tools").param("q", "cal"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymous_canListTags() throws Exception {
        when(tagService.findAll()).thenReturn(List.of(new TagResponse(1L, "Calendar", "calendar")));

        mockMvc.perform(get("/api/v1/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("calendar"));
    }

    @Test
    void anonymous_canListTagsOfPublishedTool() throws Exception {
        when(currentActorProvider.currentActor()).thenReturn(new CurrentActor(null, false));
        when(tagService.findTagsOfTool(anyLong(), any(), anyBoolean()))
                .thenReturn(List.of(new TagResponse(1L, "Calendar", "calendar")));

        mockMvc.perform(get("/api/v1/tools/1/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("calendar"));
    }

    @Test
    void anonymous_cannotCreateAdminTag() throws Exception {
        mockMvc.perform(post("/api/v1/admin/tags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Calendar\",\"slug\":\"calendar\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(tagService);
    }

    @Test
    void user_cannotCreateAdminTag() throws Exception {
        mockMvc.perform(post("/api/v1/admin/tags")
                        .with(user("member").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Calendar\",\"slug\":\"calendar\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(tagService);
    }

    @Test
    void anonymous_cannotAssignTagToTool() throws Exception {
        mockMvc.perform(post("/api/v1/tools/1/tags/2")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(tagService);
    }

    @Test
    void anonymous_cannotUnassignTagFromTool() throws Exception {
        mockMvc.perform(delete("/api/v1/tools/1/tags/2")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(tagService);
    }
}
