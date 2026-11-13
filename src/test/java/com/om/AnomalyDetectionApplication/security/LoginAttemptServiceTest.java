package com.om.AnomalyDetectionApplication.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-05-01T10:00:00Z"));
    private final LoginAttemptService service = new LoginAttemptService(clock, 5, WINDOW, 10_000);

    private void fail(String ip, String username, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(ip, username);
        }
    }

    @Test
    void fourFailures_doNotBlock() {
        fail("10.0.0.1", "admin", 4);

        assertThat(service.retryAfter("10.0.0.1", "admin")).isEmpty();
    }

    @Test
    void fiveFailures_blockForTheRestOfTheWindow() {
        fail("10.0.0.1", "admin", 5);
        clock.advance(Duration.ofMinutes(5));

        assertThat(service.retryAfter("10.0.0.1", "admin")).contains(Duration.ofMinutes(10));
    }

    @Test
    void blockEndsWhenTheWindowHasPassed() {
        fail("10.0.0.1", "admin", 5);
        clock.advance(WINDOW);

        assertThat(service.retryAfter("10.0.0.1", "admin")).isEmpty();
    }

    @Test
    void windowSlides_onlyRecentFailuresCount() {
        fail("10.0.0.1", "admin", 3);
        clock.advance(Duration.ofMinutes(10));
        fail("10.0.0.1", "admin", 2);

        assertThat(service.retryAfter("10.0.0.1", "admin")).contains(Duration.ofMinutes(5));

        clock.advance(Duration.ofMinutes(5));
        assertThat(service.retryAfter("10.0.0.1", "admin")).isEmpty();
    }

    @Test
    void oneIpTryingManyUsernames_isBlockedForEveryUsername() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("10.0.0.1", "user" + i);
        }

        assertThat(service.retryAfter("10.0.0.1", "someone-new")).isPresent();
        assertThat(service.retryAfter("10.0.0.2", "someone-new")).isEmpty();
    }

    @Test
    void oneUsernameAttackedFromManyIps_isBlockedFromEveryIp() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("10.0.0." + i, "admin");
        }

        assertThat(service.retryAfter("192.168.1.1", "admin")).isPresent();
        assertThat(service.retryAfter("192.168.1.1", "other")).isEmpty();
    }

    @Test
    void usernamesAreComparedCaseInsensitively() {
        fail("10.0.0.1", "Admin", 5);

        assertThat(service.retryAfter("10.0.0.9", "ADMIN")).isPresent();
    }

    @Test
    void successfulLogin_clearsTheHistory() {
        fail("10.0.0.1", "admin", 4);
        service.recordSuccess("10.0.0.1", "admin");
        fail("10.0.0.1", "admin", 4);

        assertThat(service.retryAfter("10.0.0.1", "admin")).isEmpty();
    }

    @Test
    void trackingIsBounded_newKeysAreIgnoredWhenFull() {
        LoginAttemptService small = new LoginAttemptService(clock, 5, WINDOW, 2);
        for (int i = 0; i < 5; i++) {
            small.recordFailure("10.0.0.1", "admin");
        }
        small.recordFailure("10.0.0.2", "other");

        assertThat(small.retryAfter("10.0.0.1", "admin")).isPresent();
        assertThat(small.retryAfter("10.0.0.2", "other")).isEmpty();
    }

    @Test
    void expiredKeysAreEvictedWhenTrackingIsFull() {
        LoginAttemptService small = new LoginAttemptService(clock, 1, WINDOW, 2);
        small.recordFailure("10.0.0.1", "admin");
        clock.advance(WINDOW.plusSeconds(1));

        small.recordFailure("10.0.0.2", "other");

        assertThat(small.retryAfter("10.0.0.2", "other")).isPresent();
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
