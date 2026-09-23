package com.example.toolhub.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RestSecurityExceptionHandlerTest {

    private final JsonMapper jsonMapper =
            JsonMapper.builder().findAndAddModules().build();
    private final RestSecurityExceptionHandler handler =
            new RestSecurityExceptionHandler(jsonMapper);

    @Test
    void commenceForApiRequestReturnsJson401() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(
                request,
                response,
                new BadCredentialsException("Bad credentials")
        );

        JsonNode body = jsonMapper.readTree(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(body.path("code").asString()).isEqualTo("UNAUTHENTICATED");
        assertThat(body.path("path").asString()).isEqualTo("/api/v1/users/me");
    }

    @Test
    void handleForApiRequestReturnsJson403() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/admin/categories");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(
                request,
                response,
                new AccessDeniedException("Denied")
        );

        JsonNode body = jsonMapper.readTree(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(403);
       assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(body.path("code").asString()).isEqualTo("ACCESS_DENIED");
        assertThat(body.path("path").asString()).isEqualTo("/api/v1/admin/categories");
    }
}
