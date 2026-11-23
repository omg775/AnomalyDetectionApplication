package com.om.AnomalyDetectionApplication.controller;

import com.om.AnomalyDetectionApplication.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Test configuration allows exactly one origin: http://localhost:5173. */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorAndCorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${app.security.admin.username}")
    private String adminUsername;

    @Test
    void healthIsPublic_andHidesDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void livenessAndReadinessArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void healthShowsDetailsToAnAuthenticatedCaller() throws Exception {
        mockMvc.perform(get("/actuator/health").header("Authorization", "Bearer " + jwtUtil.generateToken(adminUsername)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void otherActuatorEndpointsNeedAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void unexposedActuatorEndpointsAreNotFoundEvenWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/actuator/env").header("Authorization", "Bearer " + jwtUtil.generateToken(adminUsername)))
                .andExpect(status().isNotFound());
    }

    @Test
    void preflightFromAllowedOrigin_isAccepted() throws Exception {
        mockMvc.perform(options("/api/anomalies")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void preflightFromAnyOtherOrigin_isRejected() throws Exception {
        mockMvc.perform(options("/api/anomalies")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
