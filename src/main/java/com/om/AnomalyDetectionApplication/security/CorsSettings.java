package com.om.AnomalyDetectionApplication.security;

import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.List;

/**
 * Builds the CORS policy from {@code app.cors.allowed-origins}. An empty value allows no
 * cross-origin requests at all. The wildcard {@code *} is accepted only with the {@code dev}
 * profile, so a production deployment can never open the API to every website by accident.
 */
public final class CorsSettings {

    static final String WILDCARD = "*";

    private CorsSettings() {
    }

    /**
     * @param allowedOrigins comma-separated origins, for example {@code https://app.example.org}
     * @param devProfileActive whether the {@code dev} profile is active
     * @throws IllegalStateException when the wildcard is used outside the dev profile
     */
    public static CorsConfiguration build(String allowedOrigins, boolean devProfileActive) {
        List<String> origins = parse(allowedOrigins);
        CorsConfiguration configuration = new CorsConfiguration();

        if (origins.contains(WILDCARD)) {
            if (!devProfileActive) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS=* is only allowed with the 'dev' profile. "
                        + "List the frontend origins explicitly, for example https://app.example.org");
            }
            configuration.addAllowedOriginPattern(WILDCARD);
        } else {
            configuration.setAllowedOrigins(origins);
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);
        return configuration;
    }

    static List<String> parse(String allowedOrigins) {
        if (allowedOrigins == null) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}
