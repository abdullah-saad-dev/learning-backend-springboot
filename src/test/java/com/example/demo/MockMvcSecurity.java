package com.example.demo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Teaches the auto-configured MockMvc about Spring Security.
 * <p>
 * Boot 3 shipped a MockMvcSecurityConfiguration that applied {@code springSecurity()} whenever
 * spring-security-test was on the classpath. Boot 4 moved MockMvc support into
 * spring-boot-webmvc-test and that class did not come with it, so nothing wires the security
 * filter chain into MockMvc any more. Without this, {@code @WithMockUser} populates a
 * SecurityContext that the request never consults, every protected endpoint answers 401, and the
 * failure looks exactly like a misconfigured filter chain rather than a missing test hook.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MockMvcSecurity {

    @Bean
    MockMvcBuilderCustomizer springSecurityMockMvcCustomizer() {
        return builder -> builder.apply(springSecurity());
    }
}
