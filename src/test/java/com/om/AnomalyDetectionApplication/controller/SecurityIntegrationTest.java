package com.om.AnomalyDetectionApplication.controller;

import com.om.AnomalyDetectionApplication.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.admin.username}")
    private String adminUsername;

    @Test
    void missingToken_onProtectedEndpoint_returns401WithJsonError() throws Exception {
        mockMvc.perform(get("/api/anomalies"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void missingToken_onDetectEndpoint_returns401() throws Exception {
        mockMvc.perform(post("/api/traffic/detect"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedToken_returns401WithInvalidTokenMessage() throws Exception {
        mockMvc.perform(get("/api/anomalies").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    void expiredToken_returns401WithInvalidTokenMessage() throws Exception {
        JwtUtil expiredIssuer = new JwtUtil();
        ReflectionTestUtils.setField(expiredIssuer, "secret", jwtSecret);
        ReflectionTestUtils.setField(expiredIssuer, "expiration", -60_000L);
        String expired = expiredIssuer.generateToken(adminUsername);

        mockMvc.perform(get("/api/anomalies").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    void tokenSignedWithAnotherKey_returns401() throws Exception {
        JwtUtil foreignIssuer = new JwtUtil();
        ReflectionTestUtils.setField(foreignIssuer, "secret", "another-secret-that-is-at-least-32-bytes-long!!");
        ReflectionTestUtils.setField(foreignIssuer, "expiration", 60_000L);

        mockMvc.perform(get("/api/anomalies")
                        .header("Authorization", "Bearer " + foreignIssuer.generateToken(adminUsername)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    void validToken_isAccepted() throws Exception {
        mockMvc.perform(get("/api/anomalies/summary")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(adminUsername)))
                .andExpect(status().isOk());
    }

    @Test
    void loginEndpoint_isPubliclyAccessible() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void loginEndpoint_withCorrectCredentials_returnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginEndpoint_withBlankCredentials_isRejectedByValidation() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
