package com.om.AnomalyDetectionApplication.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests that carry a valid {@code Authorization: Bearer <jwt>} header. Requests
 * without a valid token continue unauthenticated, and Spring Security rejects them with a 401 if
 * the endpoint needs authentication. A present but invalid token is flagged on the request so
 * the 401 body can say so.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String INVALID_TOKEN_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".INVALID_TOKEN";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final String adminUsername;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, @Value("${app.security.admin.username}") String adminUsername) {
        this.jwtUtil = jwtUtil;
        this.adminUsername = adminUsername;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = authorizationHeader.substring(BEARER_PREFIX.length());
            if (isValid(token)) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(adminUsername, null, List.of());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isValid(String token) {
        try {
            return jwtUtil.validateToken(token, adminUsername);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
