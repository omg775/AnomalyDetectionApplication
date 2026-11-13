package com.om.AnomalyDetectionApplication.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The rate limiter keeps state in the context, so this class gets its own context. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LoginRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private ResultActions login(String ip, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void sixthAttemptAfterFiveFailures_returns429WithRetryAfter_evenWithTheRightPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("203.0.113.10", "not-admin", "wrong").andExpect(status().isUnauthorized());
        }

        login("203.0.113.10", "admin", "test-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message", containsString("Try again in")));
    }

    @Test
    void anIpSprayingUsernames_isBlocked() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("203.0.113.20", "guess" + i, "wrong").andExpect(status().isUnauthorized());
        }

        login("203.0.113.20", "guess-next", "wrong").andExpect(status().isTooManyRequests());
    }

    @Test
    void otherClients_areNotAffected() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("203.0.113.30", "intruder", "wrong");
        }

        login("203.0.113.31", "admin", "test-password").andExpect(status().isOk());
    }

    @Test
    void successfulLogin_resetsTheCounter() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("203.0.113.40", "admin", "wrong").andExpect(status().isUnauthorized());
        }
        login("203.0.113.40", "admin", "test-password").andExpect(status().isOk());

        for (int i = 0; i < 4; i++) {
            login("203.0.113.40", "admin", "wrong").andExpect(status().isUnauthorized());
        }
        login("203.0.113.40", "admin", "test-password").andExpect(status().isOk());
    }
}
