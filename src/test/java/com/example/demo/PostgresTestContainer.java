package com.example.demo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres container, shared by every test class that imports this.
 * <p>
 * The container is seeded with the real {@code db/schema.sql} rather than a test-only copy, so
 * a column the entity expects and the script forgets fails the build here instead of at
 * deployment - {@code ddl-auto=validate} means Hibernate checks the two against each other on
 * every context start.
 * <p>
 * {@code @ServiceConnection} supplies the datasource directly as a bean, which outranks the
 * {@code spring.datasource.*} values imported from secrets.env. Tests therefore never touch a
 * developer's local database, and need no credentials on CI.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainer {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:18-alpine")
                .withInitScript("db/schema.sql");
    }
}
