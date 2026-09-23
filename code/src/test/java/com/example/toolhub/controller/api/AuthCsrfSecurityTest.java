package com.example.toolhub.controller.api;

import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import com.example.toolhub.controller.api.UserProfileController;
import com.example.toolhub.service.UserProfileService;
import com.example.toolhub.dto.request.CategoryRequest;
import com.example.toolhub.dto.response.CategoryResponse;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.security.CurrentActorProvider;
import com.example.toolhub.service.CategoryService;
import com.example.toolhub.config.PasswordConfig;
import com.example.toolhub.config.SecurityConfig;
import com.example.toolhub.domain.enums.Role;
import com.example.toolhub.dto.request.RegisterRequest;
import com.example.toolhub.dto.response.UserProfileResponse;
import com.example.toolhub.security.JpaUserDetailsService;
import com.example.toolhub.security.RestSecurityExceptionHandler;
import com.example.toolhub.service.UserRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
        AuthController.class,
        CategoryRestController.class,
        UserProfileController.class
})
@Import({
        SecurityConfig.class,
        PasswordConfig.class,
        RestSecurityExceptionHandler.class
})
class AuthCsrfSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileService userProfileService;

    @MockitoBean
    private UserRegistrationService userRegistrationService;

    @MockitoBean
    private JpaUserDetailsService userDetailsService;
    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;
    @MockitoBean
private CategoryService categoryService;

@MockitoBean
private CurrentActorProvider currentActorProvider;

    @Test
    void csrfEndpoint_isPublicAndReturnsToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
    }

    @Test
    void register_withoutCsrfToken_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123",
                                  "displayName": "Nattakorn"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(userRegistrationService);
    }
    @Test
    void anonymous_cannotReadOwnProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(userProfileService);
    }
    @Test
    void register_withCsrfToken_createsUser() throws Exception {
        UserProfileResponse response = new UserProfileResponse(
                1L,
                "user@example.com",
                Role.USER,
                "Nattakorn",
                null,
                null,
                null
        );

        when(userRegistrationService.register(any(RegisterRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123",
                                  "displayName": "Nattakorn"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        verify(userRegistrationService).register(any(RegisterRequest.class));
    }
    @Test
void logout_withoutCsrfToken_returnsForbidden() throws Exception {
    mockMvc.perform(post("/api/v1/auth/logout")
                    .with(user("member").roles("USER")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
}
    @Test
void user_cannotCreateCategory() throws Exception {
    mockMvc.perform(post("/api/v1/admin/categories")
                    .with(user("member").roles("USER"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"AI","slug":"ai"}
                            """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    verifyNoInteractions(categoryService);
}

@Test
void admin_canCreateCategory() throws Exception {
    when(currentActorProvider.requireActor())
            .thenReturn(new CurrentActor(1L, true));
    when(categoryService.create(any(CategoryRequest.class), eq(true)))
            .thenReturn(new CategoryResponse(1L, "AI", "ai", null));

    mockMvc.perform(post("/api/v1/admin/categories")
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"AI","slug":"ai"}
                            """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("ai"));
}

}
