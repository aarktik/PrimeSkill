package com.example.toolhub.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.toolhub.dto.response.CategoryResponse;
import com.example.toolhub.exception.GlobalExceptionHandler;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.CategoryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CategoryRestControllerTest {
    private MockMvc mockMvc;
    private CategoryService categoryService;
    private CurrentActorProvider currentActorProvider;

    @BeforeEach
    void setUp() {
        categoryService = mock(CategoryService.class);
        currentActorProvider = mock(CurrentActorProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CategoryRestController(categoryService, currentActorProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listCategories_returnsOk() throws Exception {
        when(categoryService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void createCategory_whenActorIsNotAdmin_returnsForbidden() throws Exception {
        when(currentActorProvider.requireActor()).thenReturn(new CurrentActor(7L, false));
        when(categoryService.create(any(), org.mockito.ArgumentMatchers.eq(false)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Administrator role is required"));

        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"AI\",\"slug\":\"ai\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
