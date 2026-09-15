package com.om.AnomalyDetectionApplication.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Standalone CLI helper (not a Spring bean) to generate a BCrypt hash for
 * ADMIN_PASSWORD. Run with:
 *   mvn compile
 *   java -cp target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) \
 *     com.om.AnomalyDetectionApplication.util.PasswordHashGenerator "your-password"
 */
public final class PasswordHashGenerator {

    private PasswordHashGenerator() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: PasswordHashGenerator <plaintext-password>");
            System.exit(1);
        }
        System.out.println(new BCryptPasswordEncoder().encode(args[0]));
    }
}
