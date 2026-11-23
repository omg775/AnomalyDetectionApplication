package com.om.AnomalyDetectionApplication.security;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Prints a short, actionable startup error for unsafe security settings instead of a stack trace.
 */
public class InvalidSecuritySettingsFailureAnalyzer
        extends AbstractFailureAnalyzer<SecuritySettingsValidator.InvalidSecuritySettingsException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure,
                                      SecuritySettingsValidator.InvalidSecuritySettingsException cause) {
        String description = "The security settings are unsafe:\n  - " + String.join("\n  - ", cause.getProblems());
        String action = """
                Set the environment variables and restart:
                  JWT_SECRET      a random value of at least 32 bytes, for example: openssl rand -hex 32
                  ADMIN_PASSWORD  a BCrypt hash, see "Generating the admin password hash" in README.md""";
        return new FailureAnalysis(description, action, cause);
    }
}
