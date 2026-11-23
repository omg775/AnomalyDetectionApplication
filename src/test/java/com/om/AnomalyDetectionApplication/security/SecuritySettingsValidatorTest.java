package com.om.AnomalyDetectionApplication.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecuritySettingsValidatorTest {

    private static final String STRONG_SECRET = "0123456789abcdef0123456789abcdef";
    private static final String BCRYPT_HASH = new BCryptPasswordEncoder().encode("a-password");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SecuritySettingsValidator.class);

    @Test
    void acceptsA32ByteSecretAndABcryptHash() {
        assertThatCode(() -> SecuritySettingsValidator.validate(STRONG_SECRET, BCRYPT_HASH))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsASecretShorterThan32Bytes() {
        assertThatThrownBy(() -> SecuritySettingsValidator.validate("too-short", BCRYPT_HASH))
                .isInstanceOf(SecuritySettingsValidator.InvalidSecuritySettingsException.class)
                .hasMessageContaining("JWT_SECRET is 9 bytes long")
                .hasMessageNotContaining("too-short");
    }

    @Test
    void countsBytesNotCharacters() {
        String sixteenTwoByteCharacters = "é".repeat(16);

        assertThatCode(() -> SecuritySettingsValidator.validate(sixteenTwoByteCharacters, BCRYPT_HASH))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAPlaintextAdminPassword() {
        assertThatThrownBy(() -> SecuritySettingsValidator.validate(STRONG_SECRET, "password123"))
                .isInstanceOf(SecuritySettingsValidator.InvalidSecuritySettingsException.class)
                .hasMessageContaining("ADMIN_PASSWORD is not a BCrypt hash")
                .hasMessageNotContaining("password123");
    }

    @Test
    void rejectsAPlaceholderThatOnlyLooksLikeAHash() {
        assertThatThrownBy(() -> SecuritySettingsValidator.validate(STRONG_SECRET, "$2a$10$change.me.to.a.real.hash"))
                .isInstanceOf(SecuritySettingsValidator.InvalidSecuritySettingsException.class);
    }

    @Test
    void reportsEveryProblemAtOnce() {
        assertThatThrownBy(() -> SecuritySettingsValidator.validate("", ""))
                .isInstanceOfSatisfying(SecuritySettingsValidator.InvalidSecuritySettingsException.class,
                        e -> assertThat(e.getProblems()).hasSize(2));
    }

    @Test
    void applicationContextFailsToStartWithAWeakSecret() {
        contextRunner
                .withPropertyValues("jwt.secret=short", "app.security.admin.password=" + BCRYPT_HASH)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause()
                        .isInstanceOf(SecuritySettingsValidator.InvalidSecuritySettingsException.class));
    }

    @Test
    void applicationContextStartsWithSafeSettings() {
        contextRunner
                .withPropertyValues("jwt.secret=" + STRONG_SECRET, "app.security.admin.password=" + BCRYPT_HASH)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failureAnalyzerExplainsWhatToSet() {
        try {
            SecuritySettingsValidator.validate("short", "plain");
        } catch (SecuritySettingsValidator.InvalidSecuritySettingsException e) {
            FailureAnalysis analysis = new InvalidSecuritySettingsFailureAnalyzer().analyze(e);

            assertThat(analysis.getDescription()).contains("JWT_SECRET", "ADMIN_PASSWORD");
            assertThat(analysis.getAction()).contains("openssl rand -hex 32");
            return;
        }
        throw new AssertionError("validate should have thrown");
    }
}
