package com.example.demo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres container, shared by every test class that imports this.
 * <p>
 * The container starts empty: Flyway owns the schema, so it applies the real migration chain
 * from {@code db/migration} on context start, exactly as it does in production. Seeding the
 * container with an init script instead would create the tables behind Flyway's back, leaving
 * it to find a populated database with no {@code flyway_schema_history} - and fail. The payoff
 * is that every test run now proves the migrations apply cleanly and in order, and
 * {@code ddl-auto=validate} then checks the entities against whatever they produced, so a
 * column an entity expects and a migration forgets fails the build here rather than at deployment.
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
        return new PostgreSQLContainer<>("postgres:18-alpine");
    }
}
