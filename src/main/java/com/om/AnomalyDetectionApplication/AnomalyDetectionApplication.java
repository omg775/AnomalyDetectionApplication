package com.om.AnomalyDetectionApplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Authentication is JWT only (see SecurityConfig), so Spring Boot's default in-memory user, whose
 * generated password would otherwise be printed to the log at startup, is switched off.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AnomalyDetectionApplication {

	public static void main(String[] args) {
		SpringApplication.run(AnomalyDetectionApplication.class, args);
	}

}
