package com.example.toolhub.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.example.toolhub.exception.ResourceNotFoundException;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.exception.AuthenticationRequiredException;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.GlobalExceptionHandler;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.ToolService;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ToolRestControllerTest {
    private MockMvc mockMvc;
    private ToolService toolService;
    private CurrentActorProvider currentActorProvider;

    @BeforeEach
    void setUp() {
        toolService = mock(ToolService.class);
        currentActorProvider = mock(CurrentActorProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ToolRestController(toolService, currentActorProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getBySlug_returnsOkForAnonymousRequest() throws Exception {
        when(currentActorProvider.currentActor()).thenReturn(new CurrentActor(null, false));
        when(toolService.getByIdOrSlug(anyString(), isNull(), anyBoolean()))
                .thenReturn(ToolResponse.builder().id(1L).name("Calendar").slug("calendar").build());

        mockMvc.perform(get("/api/v1/tools/calendar"))
                .andExpect(status().isOk());
    }

    @Test
    void create_whenRequestIsInvalid_returnsStandardValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"slug\":\"INVALID SLUG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void create_whenSlugConflicts_returnsConflict() throws Exception {
        when(currentActorProvider.requireActor()).thenReturn(new CurrentActor(7L, false));
        when(toolService.create(any(), anyLong())).thenThrow(new CatalogConflictException("Tool slug already exists"));

        mockMvc.perform(post("/api/v1/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Calendar\",\"slug\":\"calendar\",\"shortDescription\":\"Helper\",\"description\":\"Details\",\"categoryId\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    @Test
    void create_whenAuthenticationIsMissing_returnsUnauthorized() throws Exception {
        when(currentActorProvider.requireActor()).thenThrow(new AuthenticationRequiredException());

        mockMvc.perform(post("/api/v1/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Calendar\",\"slug\":\"calendar\",\"shortDescription\":\"Helper\",\"description\":\"Details\",\"categoryId\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void create_whenUniqueConstraintFails_returnsConflictWithoutDatabaseMessage() throws Exception {
        when(currentActorProvider.requireActor()).thenReturn(new CurrentActor(7L, false));
        when(toolService.create(any(), anyLong()))
                .thenThrow(new DataIntegrityViolationException("constraint uq_tools_slug failed",
                        new ConstraintViolationException("duplicate slug",
                                new SQLException("duplicate", "23505"), "tools_slug_key")));

        mockMvc.perform(post("/api/v1/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Calendar\",\"slug\":\"calendar\",\"shortDescription\":\"Helper\",\"description\":\"Details\",\"categoryId\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Resource conflicts with existing data"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void create_whenIntegrityFailureIsNotAConflict_returnsSafeServerError() throws Exception {
        when(currentActorProvider.requireActor()).thenReturn(new CurrentActor(7L, false));
        when(toolService.create(any(), anyLong()))
                .thenThrow(new DataIntegrityViolationException("null value in internal field"));

        mockMvc.perform(post("/api/v1/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Calendar\",\"slug\":\"calendar\",\"shortDescription\":\"Helper\",\"description\":\"Details\",\"categoryId\":1}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
    @Test
void getBySlug_whenToolDoesNotExist_returnsNotFoundError() throws Exception {
    when(currentActorProvider.currentActor())
            .thenReturn(new CurrentActor(null, false));
    when(toolService.getByIdOrSlug(anyString(), isNull(), anyBoolean()))
            .thenThrow(new ResourceNotFoundException("Tool not found"));

    mockMvc.perform(get("/api/v1/tools/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.path").value("/api/v1/tools/missing"));
}
}