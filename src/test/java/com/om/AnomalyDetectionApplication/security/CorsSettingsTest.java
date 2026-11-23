package com.om.AnomalyDetectionApplication.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsSettingsTest {

    @Test
    void emptyValue_allowsNoOrigin() {
        CorsConfiguration configuration = CorsSettings.build("", false);

        assertThat(configuration.getAllowedOrigins()).isEmpty();
        assertThat(configuration.getAllowedOriginPatterns()).isNull();
        assertThat(configuration.checkOrigin("https://any.example")).isNull();
    }

    @Test
    void explicitOrigins_areTrimmedAndBlankEntriesIgnored() {
        CorsConfiguration configuration = CorsSettings.build(" https://a.example , ,https://b.example", false);

        assertThat(configuration.getAllowedOrigins()).containsExactly("https://a.example", "https://b.example");
        assertThat(configuration.checkOrigin("https://c.example")).isNull();
    }

    @Test
    void wildcard_isRejectedOutsideTheDevProfile() {
        assertThatThrownBy(() -> CorsSettings.build("*", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only allowed with the 'dev' profile");
    }

    @Test
    void wildcardMixedWithOrigins_isAlsoRejectedOutsideTheDevProfile() {
        assertThatThrownBy(() -> CorsSettings.build("https://a.example,*", false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wildcard_isAcceptedWithTheDevProfile() {
        CorsConfiguration configuration = CorsSettings.build("*", true);

        assertThat(configuration.checkOrigin("https://any.example")).isEqualTo("https://any.example");
    }

    @Test
    void credentialsAreNeverAllowed() {
        assertThat(CorsSettings.build("https://a.example", false).getAllowCredentials()).isFalse();
    }
}
