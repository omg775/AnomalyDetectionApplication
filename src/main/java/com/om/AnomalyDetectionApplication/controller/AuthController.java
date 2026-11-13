package com.om.AnomalyDetectionApplication.controller;

import com.om.AnomalyDetectionApplication.exception.TooManyLoginAttemptsException;
import com.om.AnomalyDetectionApplication.security.JwtUtil;
import com.om.AnomalyDetectionApplication.security.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    @Value("${app.security.admin.username}")
    private String adminUsername;

    /** BCrypt hash of the admin password (see PasswordHashGenerator to create one). */
    @Value("${app.security.admin.password}")
    private String adminPasswordHash;

    public AuthController(JwtUtil jwtUtil, PasswordEncoder passwordEncoder, LoginAttemptService loginAttemptService) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> createAuthenticationToken(@Valid @RequestBody AuthRequest authRequest,
                                                       HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        String username = authRequest.getUsername();

        loginAttemptService.retryAfter(clientIp, username).ifPresent(wait -> {
            throw new TooManyLoginAttemptsException(wait);
        });

        if (adminUsername.equals(username)
                && passwordEncoder.matches(authRequest.getPassword(), adminPasswordHash)) {
            loginAttemptService.recordSuccess(clientIp, username);
            final String jwt = jwtUtil.generateToken(adminUsername);
            return ResponseEntity.ok(Map.of("token", jwt));
        }

        loginAttemptService.recordFailure(clientIp, username);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
    }

    public static class AuthRequest {
        @NotBlank
        private String username;

        @NotBlank
        private String password;

        public AuthRequest() {}

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
