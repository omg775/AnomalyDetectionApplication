package com.om.AnomalyDetectionApplication.migration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Starts a throwaway PostgreSQL container and points the Spring context at it with the
 * same Flyway and Hikari settings as production (pool size 1, as on the Supabase session pooler).
 */
final class PostgresTestDatabase {

    private static final String IMAGE = "postgres:17-alpine";

    private PostgresTestDatabase() {
    }

    static PostgreSQLContainer start() {
        PostgreSQLContainer container = new PostgreSQLContainer(IMAGE);
        container.start();
        return container;
    }

    static void register(DynamicPropertyRegistry registry, PostgreSQLContainer container) {
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "1");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    static void execute(PostgreSQLContainer container, String... statements) {
        try (Connection connection = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to prepare test database", e);
        }
    }
}
