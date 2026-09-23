package com.example.toolhub.controller.api;

import com.example.toolhub.domain.enums.Role;
import com.example.toolhub.dto.request.UpdateUserProfileRequest;
import com.example.toolhub.dto.response.UserProfileResponse;
import com.example.toolhub.exception.GlobalExceptionHandler;
import com.example.toolhub.security.UserPrincipal;
import com.example.toolhub.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileControllerTest {

    private MockMvc mockMvc;
    private UserProfileService userProfileService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        userProfileService = mock(UserProfileService.class);

        UserPrincipal userPrincipal = mock(UserPrincipal.class);
        when(userPrincipal.getId()).thenReturn(1L);

        authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal,
                null
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new UserProfileController(userProfileService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMyProfile_returnsCurrentUsersProfile() throws Exception {
        UserProfileResponse response = new UserProfileResponse(
                1L,
                "user@example.com",
                Role.USER,
                "Nattakorn",
                "My bio",
                null,
                null
        );

        when(userProfileService.getMyProfile(1L))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/users/me")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.displayName").value("Nattakorn"));

        verify(userProfileService).getMyProfile(1L);
    }

    @Test
    void updateMyProfile_withValidRequest_returnsUpdatedProfile()
            throws Exception {

        UpdateUserProfileRequest request =
                new UpdateUserProfileRequest(
                        "New Name",
                        "New bio",
                        "https://example.com/avatar.png"
                );

        UserProfileResponse response = new UserProfileResponse(
                1L,
                "user@example.com",
                Role.USER,
                "New Name",
                "New bio",
                "https://example.com/avatar.png",
                null
        );

        when(userProfileService.updateMyProfile(
                eq(1L),
                any(UpdateUserProfileRequest.class)
        )).thenReturn(response);

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New Name"))
                .andExpect(jsonPath("$.bio").value("New bio"))
                .andExpect(jsonPath("$.avatarUrl")
                        .value("https://example.com/avatar.png"));

        verify(userProfileService).updateMyProfile(
                eq(1L),
                any(UpdateUserProfileRequest.class)
        );
    }

    @Test
    void updateMyProfile_withInvalidRequest_returnsBadRequest()
            throws Exception {

        String invalidJson = """
                {
                  "displayName": "",
                  "bio": "New bio",
                  "avatarUrl": null
                }
                """;

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userProfileService);
    }
}
