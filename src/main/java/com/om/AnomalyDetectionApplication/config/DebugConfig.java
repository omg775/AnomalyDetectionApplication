package com.om.AnomalyDetectionApplication.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Dev-only diagnostics. Never active by default — activate with
 * spring.profiles.active=dev. Deliberately never logs the datasource
 * password.
 */
@Configuration
@Profile("dev")
public class DebugConfig {

    private static final Logger log = LoggerFactory.getLogger(DebugConfig.class);

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Bean
    CommandLineRunner showDbConfig() {
        return args -> {
            log.debug("DB URL = {}", url);
            log.debug("DB USER = {}", username);
        };
    }
}
