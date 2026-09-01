package com.example.demo.auth;

import com.example.demo.MockMvcSecurity;
import com.example.demo.PostgresTestContainer;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.enums.Role;
import com.example.demo.auth.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rotation, reuse detection, and the grace window, end to end through the HTTP layer.
 * <p>
 * These paths are invisible to the login tests: every one of them answers with an identical 401,
 * so the only way to tell a benign two-tab race from a replayed stolen token is to look at what
 * happened to the rows. Each test therefore asserts on the database as well as the status code.
 * <p>
 * The context supplies a movable clock in place of the real one, because the 30-second grace
 * window and the 30-day absolute cap cannot be reached against a clock that will not move.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainer.class, MockMvcSecurity.class, RefreshApiTest.FixedClock.class})
class RefreshApiTest {

    private static final String EMAIL = "rotator@example.com";
    private static final String PASSWORD = "correct-horse-battery-staple";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MutableClock clock;

    @BeforeEach
    void seed() {
        jdbc.execute("truncate table users cascade");
        clock.reset();
        users.save(User.builder()
                .username("rotator")
                .email(EMAIL)
                .password(passwordEncoder.encode(PASSWORD))
                .enabled(true)
                .role(Role.USER)
                .build());
    }

    // ---------- the cookie itself ----------

    @Test
    void loginSetsAHardenedRefreshCookieScopedToTheRefreshEndpoint() throws Exception {
        String setCookie = login().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).isNotNull();
        assertThat(setCookie)
                .contains("refreshToken=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                // scoped so the long-lived credential rides on the one request that needs it,
                // rather than on every call to the API
                .contains("Path=/auth/refresh");
    }

    @Test
    void theRawRefreshTokenNeverAppearsInTheResponseBody() throws Exception {
        MvcResult result = login();
        String raw = cookieValueOf(result);

        assertThat(result.getResponse().getContentAsString()).doesNotContain(raw);
    }

    // ---------- the happy path ----------

    @Test
    void refreshingRotatesTheTokenAndIssuesANewAccessToken() throws Exception {
        String first = cookieValueOf(login());

        MvcResult refreshed = mvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        assertThat(cookieValueOf(refreshed))
                .as("a refresh must hand back a different token, never the one it was given")
                .isNotEqualTo(first);
        assertThat(countWithStatus("ROTATED")).isOne();
        assertThat(countWithStatus("ACTIVE")).isOne();
    }

    @Test
    void theSuccessorInheritsTheFamilyAndTheAbsoluteCapUnchanged() throws Exception {
        String first = cookieValueOf(login());
        // Move the clock before rotating. Without this, a recomputed cap would land on the same
        // instant as the copied one and the assertion below could not tell them apart.
        clock.advance(Duration.ofHours(1));
        mvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", first)))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "select count(distinct family_id) from refresh_tokens", Integer.class))
                .as("the successor stays in the parent family")
                .isOne();
        assertThat(jdbc.queryForObject(
                "select count(distinct absolute_expires_at) from refresh_tokens", Integer.class))
                .as("the absolute cap is copied, never recomputed, or the session never ends")
                .isOne();
    }

    // ---------- the two-tab race ----------

    @Test
    void aReplayInsideTheGraceWindowIsRejectedButLeavesTheFamilyAlone() throws Exception {
        String first = cookieValueOf(login());
        mvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", first)))
                .andExpect(status().isOk());

        // the second tab, refreshing with the same token moments later
        mvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", first)))
                .andExpect(status().isUnauthorized());

        assertThat(countWithStatus("REVOKED"))
                .as("a sibling tab is not an attacker, so nothing may be revoked")
                .isZero();
        assertThat(countWithStatus("ACTIVE"))
                .as("the live successor must survive")
                .isOne();
    }

    // ---------- actual reuse ----------

    @Test
    void aReplayAfterTheGraceWindowRevokesTheWholeFamily() throws Exception {
        String first = cookieValueOf(login());
        mvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", first)))
                .andExpect(status().isOk());

        clock.advance(Duration.ofSeconds(31));

        mvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", first)))
                .andExpect(status().isUnauthorized());

        assertThat(countWithStatus("ACTIVE"))
                .as("reuse this stale means the token was stolen, so no session in the family survives")
                .isZero();
        assertThat(countWithStatus("REVOKED")).isOne();
    }

    @Test
    void anExpiredTokenCannotRefresh() throws Exception {
        String first = cookieValueOf(login());

        clock.advance(Duration.ofDays(8)); // past the 7-day sliding expiry

        mvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", first)))
                .andExpect(status().isUnauthorized());
        assertThat(countWithStatus("ROTATED"))
                .as("an expired token must not be rotated")
                .isZero();
    }

    @Test
    void theAbsoluteCapEventuallyEndsTheSession() throws Exception {
        String raw = cookieValueOf(login());

        // Keep refreshing inside the sliding window. Only the absolute cap can stop this;
        // if it is ever recomputed instead of copied, the loop never terminates.
        for (int hop = 0; hop < 5; hop++) {
            clock.advance(Duration.ofDays(6));
            MvcResult result = mvc.perform(post("/auth/refresh")
                    .cookie(new Cookie("refreshToken", raw))).andReturn();

            if (result.getResponse().getStatus() == 401) {
                assertThat(hop)
                        .as("the 30-day cap should bite on the 5th hop (day 30), not before")
                        .isEqualTo(4);
                return;
            }
            raw = cookieValueOf(result);
        }
        throw new AssertionError("the absolute cap never applied: this session is immortal");
    }

    // ---------- rejected input ----------

    @Test
    void anUnknownTokenIsRejected() throws Exception {
        login();
        mvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", "not-a-real-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRequestWithNoCookieIsRejected() throws Exception {
        mvc.perform(post("/auth/refresh"))
                .andExpect(status().is4xxClientError());
    }

    // ---------- helpers ----------

    private MvcResult login() throws Exception {
        return mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** Set-Cookie carries attributes too; the token is everything before the first semicolon. */
    private String cookieValueOf(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).isNotNull();
        return header.split(";", 2)[0].split("=", 2)[1];
    }

    private Integer countWithStatus(String status) {
        return jdbc.queryForObject(
                "select count(*) from refresh_tokens where status = ?", Integer.class, status);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    /** A clock the test can push forward, so both expiries and the grace window are reachable. */
    static class MutableClock extends Clock {
        private static final Instant START = Instant.parse("2026-03-01T12:00:00Z");
        private Instant instant = START;

        void reset() {
            instant = START;
        }

        void advance(Duration amount) {
            instant = instant.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
