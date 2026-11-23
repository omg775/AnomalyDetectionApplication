package com.om.AnomalyDetectionApplication.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Stops the application at startup when the security settings are unsafe, instead of failing
 * later on the first login or token check. Never includes the secret values in its messages.
 */
@Component
public class SecuritySettingsValidator {

    /** HS256 needs a key of at least 256 bits. */
    public static final int MIN_JWT_SECRET_BYTES = 32;

    private static final Pattern BCRYPT_HASH = Pattern.compile("^\\$2[abxy]?\\$(0[4-9]|[12]\\d|3[01])\\$[./A-Za-z0-9]{53}$");

    public SecuritySettingsValidator(@Value("${jwt.secret}") String jwtSecret,
                                     @Value("${app.security.admin.password}") String adminPasswordHash) {
        validate(jwtSecret, adminPasswordHash);
    }

    /**
     * Checks the JWT secret length and the admin password hash format.
     *
     * @throws InvalidSecuritySettingsException listing every problem found
     */
    public static void validate(String jwtSecret, String adminPasswordHash) {
        List<String> problems = new ArrayList<>();

        int secretBytes = jwtSecret == null ? 0 : jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (secretBytes < MIN_JWT_SECRET_BYTES) {
            problems.add("JWT_SECRET is " + secretBytes + " bytes long but HS256 needs at least "
                    + MIN_JWT_SECRET_BYTES + " bytes (256 bits).");
        }
        if (adminPasswordHash == null || !BCRYPT_HASH.matcher(adminPasswordHash).matches()) {
            problems.add("ADMIN_PASSWORD is not a BCrypt hash. It must be the hash of the password "
                    + "(starting with $2a$, $2b$ or $2y$), never the plaintext password.");
        }

        if (!problems.isEmpty()) {
            throw new InvalidSecuritySettingsException(problems);
        }
    }

    /** Raised when the security environment variables are missing or unsafe. */
    public static class InvalidSecuritySettingsException extends IllegalStateException {

        private final List<String> problems;

        InvalidSecuritySettingsException(List<String> problems) {
            super("Unsafe security settings: " + String.join(" ", problems));
            this.problems = List.copyOf(problems);
        }

        public List<String> getProblems() {
            return problems;
        }
    }
}
