package com.om.AnomalyDetectionApplication.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force protection for the login endpoint. Failed attempts are counted in a
 * sliding window, separately per client IP and per username, so neither spraying one password
 * across many accounts nor attacking one account from many addresses gets unlimited tries.
 * State lives in this JVM only and is lost on restart.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final int maxTrackedKeys;

    public LoginAttemptService(Clock clock,
                               @Value("${app.security.login.max-failures:5}") int maxFailures,
                               @Value("${app.security.login.window:15m}") Duration window,
                               @Value("${app.security.login.max-tracked-keys:10000}") int maxTrackedKeys) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
        this.maxTrackedKeys = maxTrackedKeys;
    }

    /**
     * Tells whether a login attempt must be refused before the password is even checked.
     *
     * @param clientIp the caller's IP address
     * @param username the submitted username
     * @return how long the caller must wait, or empty when the attempt may proceed
     */
    public Optional<Duration> retryAfter(String clientIp, String username) {
        Instant now = clock.instant();
        Duration ipWait = waitFor(ipKey(clientIp), now);
        Duration userWait = waitFor(userKey(username), now);
        Duration longest = ipWait.compareTo(userWait) >= 0 ? ipWait : userWait;
        return longest.isZero() ? Optional.empty() : Optional.of(longest);
    }

    /**
     * Records a failed login for both the client IP and the username.
     *
     * @param clientIp the caller's IP address
     * @param username the submitted username
     */
    public void recordFailure(String clientIp, String username) {
        Instant now = clock.instant();
        if (failures.size() >= maxTrackedKeys) {
            evictExpired(now);
        }
        addFailure(ipKey(clientIp), now);
        addFailure(userKey(username), now);
    }

    /**
     * Clears the failure history of the client IP and the username after a successful login.
     *
     * @param clientIp the caller's IP address
     * @param username the authenticated username
     */
    public void recordSuccess(String clientIp, String username) {
        failures.remove(ipKey(clientIp));
        failures.remove(userKey(username));
    }

    private void addFailure(String key, Instant now) {
        if (!failures.containsKey(key) && failures.size() >= maxTrackedKeys) {
            log.warn("Login attempt tracking is full ({} keys); not tracking a new key", maxTrackedKeys);
            return;
        }
        failures.compute(key, (k, attempts) -> {
            Deque<Instant> updated = attempts == null ? new ArrayDeque<>() : attempts;
            prune(updated, now);
            updated.addLast(now);
            return updated;
        });
    }

    private Duration waitFor(String key, Instant now) {
        Duration[] wait = {Duration.ZERO};
        failures.computeIfPresent(key, (k, attempts) -> {
            prune(attempts, now);
            if (attempts.size() >= maxFailures) {
                Instant oldestRelevant = attempts.toArray(Instant[]::new)[attempts.size() - maxFailures];
                wait[0] = Duration.between(now, oldestRelevant.plus(window));
            }
            return attempts.isEmpty() ? null : attempts;
        });
        return wait[0];
    }

    private void evictExpired(Instant now) {
        failures.replaceAll((key, attempts) -> {
            prune(attempts, now);
            return attempts;
        });
        failures.values().removeIf(Deque::isEmpty);
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        Instant cutoff = now.minus(window);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.removeFirst();
        }
    }

    private static String ipKey(String clientIp) {
        return "ip:" + clientIp;
    }

    private static String userKey(String username) {
        return "user:" + (username == null ? "" : username.toLowerCase(Locale.ROOT));
    }
}
