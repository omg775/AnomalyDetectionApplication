package com.om.AnomalyDetectionApplication.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-for-jwt-util-tests-0123456789abcdef";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600000L);
    }

    @Test
    void generateToken_thenExtractUsername_roundTrips() {
        String token = jwtUtil.generateToken("admin");

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("admin");
    }

    @Test
    void generateToken_expirationIsInTheFuture() {
        String token = jwtUtil.generateToken("admin");

        assertThat(jwtUtil.extractExpiration(token)).isAfter(new java.util.Date());
    }

    @Test
    void validateToken_correctUsername_returnsTrue() {
        String token = jwtUtil.generateToken("admin");

        assertThat(jwtUtil.validateToken(token, "admin")).isTrue();
    }

    @Test
    void validateToken_wrongUsername_returnsFalse() {
        String token = jwtUtil.generateToken("admin");

        assertThat(jwtUtil.validateToken(token, "someone-else")).isFalse();
    }

    @Test
    void expiredToken_throwsOnParse() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", -1000L);
        String expiredToken = jwtUtil.generateToken("admin");

        assertThatThrownBy(() -> jwtUtil.extractUsername(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedToken_failsToParse() {
        String token = jwtUtil.generateToken("admin");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtUtil.extractUsername(tampered))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void tokenSignedWithDifferentSecret_failsValidation() {
        String token = jwtUtil.generateToken("admin");

        JwtUtil otherUtil = new JwtUtil();
        ReflectionTestUtils.setField(otherUtil, "secret", "a-completely-different-secret-key-0123456789ab");
        ReflectionTestUtils.setField(otherUtil, "expiration", 3600000L);

        assertThatThrownBy(() -> otherUtil.extractUsername(token))
                .isInstanceOf(RuntimeException.class);
    }
}
