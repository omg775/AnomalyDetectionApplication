package com.om.AnomalyDetectionApplication.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** The only endpoints reachable without a token. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/login",
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness"
    };

    private final JwtAuthenticationFilter jwtRequestFilter;
    private final RestSecurityErrorHandler securityErrorHandler;
    private final String allowedOrigins;
    private final boolean devProfileActive;

    public SecurityConfig(JwtAuthenticationFilter jwtRequestFilter,
                          RestSecurityErrorHandler securityErrorHandler,
                          @Value("${app.cors.allowed-origins:}") String allowedOrigins,
                          Environment environment) {
        this.jwtRequestFilter = jwtRequestFilter;
        this.securityErrorHandler = securityErrorHandler;
        this.allowedOrigins = allowedOrigins;
        this.devProfileActive = environment.acceptsProfiles(Profiles.of("dev"));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(securityErrorHandler)
                .accessDeniedHandler(securityErrorHandler));

        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", CorsSettings.build(allowedOrigins, devProfileActive));
        return source;
    }
}
