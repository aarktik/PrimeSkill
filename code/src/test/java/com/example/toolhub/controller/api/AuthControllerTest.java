package com.example.toolhub.controller.api;

import com.example.toolhub.domain.enums.Role;
import com.example.toolhub.dto.request.RegisterRequest;
import com.example.toolhub.dto.response.UserProfileResponse;
import com.example.toolhub.exception.GlobalExceptionHandler;
import com.example.toolhub.service.UserRegistrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private UserRegistrationService userRegistrationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userRegistrationService = mock(UserRegistrationService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(userRegistrationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void register_withValidRequest_returnsCreatedUser() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "user@example.com",
                "password123",
                "Nattakorn"
        );

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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.displayName").value("Nattakorn"));

        verify(userRegistrationService).register(any(RegisterRequest.class));
    }

    @Test
    void register_withInvalidEmail_returnsBadRequest() throws Exception {
        String invalidJson = """
                {
                  "email": "wrong-email",
                  "password": "password123",
                  "displayName": "Nattakorn"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userRegistrationService);
    }
}