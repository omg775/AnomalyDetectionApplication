package com.om.AnomalyDetectionApplication.migration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An empty PostgreSQL database: Flyway runs every migration from V1 and Hibernate's
 * ddl-auto=validate accepts the result. Skipped automatically when Docker is not available.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class FreshDatabaseMigrationTest {

    private static PostgreSQLContainer container;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        container = PostgresTestDatabase.start();
        PostgresTestDatabase.register(registry, container);
    }

    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void allMigrationsApplySuccessfullyFromScratch() {
        Integer failed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false", Integer.class);
        String firstVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank LIMIT 1", String.class);

        assertThat(failed).isZero();
        assertThat(firstVersion).isEqualTo("1");
    }
}
