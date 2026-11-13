package com.om.AnomalyDetectionApplication.exception;

import java.time.Duration;

/** Thrown when a login is refused because of too many recent failed attempts. */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super("Too many failed login attempts");
        this.retryAfter = retryAfter;
    }

    /** Whole seconds the client has to wait, rounded up and at least 1. */
    public long getRetryAfterSeconds() {
        long seconds = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }
}
