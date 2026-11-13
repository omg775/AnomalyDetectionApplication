package com.om.AnomalyDetectionApplication.security;

import com.om.AnomalyDetectionApplication.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Turns Spring Security failures into the same JSON {@link ErrorResponse} body the rest of the
 * API uses: 401 when the request has no valid token, 403 when an authenticated caller may not
 * access the resource.
 */
@Component
public class RestSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String MISSING_TOKEN_MESSAGE = "Authentication required";
    static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";
    static final String ACCESS_DENIED_MESSAGE = "Access denied";

    private final JsonMapper jsonMapper;

    public RestSecurityErrorHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean invalidToken = request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE) != null;
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                invalidToken ? "Bearer error=\"invalid_token\"" : "Bearer");
        write(response, HttpStatus.UNAUTHORIZED, invalidToken ? INVALID_TOKEN_MESSAGE : MISSING_TOKEN_MESSAGE);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, ACCESS_DENIED_MESSAGE);
    }

    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ErrorResponse.of(status, message));
    }
}
