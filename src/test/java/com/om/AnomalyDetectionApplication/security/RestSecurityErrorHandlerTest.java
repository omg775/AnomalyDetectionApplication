package com.om.AnomalyDetectionApplication.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RestSecurityErrorHandlerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final RestSecurityErrorHandler handler = new RestSecurityErrorHandler(jsonMapper);

    @Test
    void accessDenied_writes403InErrorResponseFormat() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("no"));

        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("error").asString()).isEqualTo("Forbidden");
        assertThat(body.get("message").asString()).isEqualTo("Access denied");
        assertThat(body.has("timestamp")).isTrue();
    }

    @Test
    void missingAuthentication_writes401WithBearerChallenge() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("no"));

        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(body.get("error").asString()).isEqualTo("Unauthorized");
        assertThat(body.get("message").asString()).isEqualTo("Authentication required");
    }

    @Test
    void invalidToken_writes401WithInvalidTokenChallenge() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response, new InsufficientAuthenticationException("no"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer error=\"invalid_token\"");
        assertThat(jsonMapper.readTree(response.getContentAsString()).get("message").asString())
                .isEqualTo("Invalid or expired token");
    }
}
