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
import org.springframework.security.authentication.AuthenticationManager;
import com.example.toolhub.domain.entity.User;
import com.example.toolhub.dto.request.LoginRequest;
import com.example.toolhub.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

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
    private AuthenticationManager authenticationManager;

    @BeforeEach
    void setUp() {
        userRegistrationService = mock(UserRegistrationService.class);
        authenticationManager = mock(AuthenticationManager.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(
        new AuthController(userRegistrationService, authenticationManager)
)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
    @AfterEach
        void tearDown() {
         SecurityContextHolder.clearContext();
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
    @Test
void login_withValidCredentials_createsSessionAndReturnsUser()
        throws Exception {

    User user = new User("user@example.com", "encoded-password");
    user.setRole(Role.USER);

    UserPrincipal principal = new UserPrincipal(user);

    Authentication authentication =
            new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );

    when(authenticationManager.authenticate(any(Authentication.class)))
            .thenReturn(authentication);

    LoginRequest loginRequest = new LoginRequest(
            "USER@EXAMPLE.COM",
            "password123"
    );

    mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(request().sessionAttribute(
                    HttpSessionSecurityContextRepository
                            .SPRING_SECURITY_CONTEXT_KEY,
                    notNullValue()
            ));

    verify(authenticationManager).authenticate(any(Authentication.class));
}

@Test
void login_withInvalidEmail_returnsBadRequest() throws Exception {
    String invalidJson = """
            {
              "email": "wrong-email",
              "password": "password123"
            }
            """;

    mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidJson))
            .andExpect(status().isBadRequest());

    verifyNoInteractions(authenticationManager);
}
@Test
void logout_invalidatesSessionAndClearsSecurityContext()
        throws Exception {

    Authentication authentication =
            new UsernamePasswordAuthenticationToken(
                    "user@example.com",
                    "unused-password"
            );

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);

    MockHttpSession session = new MockHttpSession();
    session.setAttribute(
            HttpSessionSecurityContextRepository
                    .SPRING_SECURITY_CONTEXT_KEY,
            context
    );

    mockMvc.perform(post("/api/v1/auth/logout")
                    .session(session))
            .andExpect(status().isNoContent());

    assertThat(session.isInvalid()).isTrue();
    assertThat(SecurityContextHolder.getContext().getAuthentication())
            .isNull();
}
}