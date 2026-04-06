package com.om.AnomalyDetectionApplication.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DebugConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Bean
    CommandLineRunner showDbConfig() {
        return args -> {
            System.out.println("DB URL = " + url);
            System.out.println("DB USER = " + username);
        };
    }
}