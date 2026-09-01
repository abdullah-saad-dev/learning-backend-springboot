package com.example.demo.auth;

import com.example.demo.MockMvcSecurity;
import com.example.demo.PostgresTestContainer;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.enums.Role;
import com.example.demo.auth.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The authentication path end to end: a password becomes a token, and that token opens a
 * protected endpoint. Runs against a real database and the real filter chain, because every
 * interesting failure here lives in the wiring rather than in a single class - a mis-imported
 * exception type, a principal that is not what the cast assumed, a claim the converter reads
 * under a different name.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainer.class, MockMvcSecurity.class})
class AuthApiTest {

    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final String ACTIVE_EMAIL = "active@example.com";
    private static final String DISABLED_EMAIL = "disabled@example.com";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID activeUserId;

    @BeforeEach
    void seedUsers() {
        // CASCADE because refresh_tokens has an FK to users; a bare TRUNCATE is refused
        // outright rather than cascading by default.
        jdbc.execute("truncate table users cascade");
        activeUserId = users.save(user(ACTIVE_EMAIL, true)).getId();
        users.save(user(DISABLED_EMAIL, false));
    }

    private User user(String email, boolean enabled) {
        return User.builder()
                .username(email.substring(0, email.indexOf('@')))
                .email(email)
                // Encoded through the real encoder rather than a pasted literal, so the test
                // still passes the day the default algorithm behind the {prefix} changes.
                .password(passwordEncoder.encode(PASSWORD))
                .enabled(enabled)
                .role(Role.USER)
                .build();
    }

    // ---------- signup ----------

    @Test
    void signupCreatesAUsableAccount() throws Exception {
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"newbie","email":"newbie@example.com","password":"%s"}"""
                                .formatted(PASSWORD)))
                .andExpect(status().isOk());

        // The account is only real if it can authenticate; a row alone proves nothing.
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"newbie@example.com","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void aDuplicateEmailIsA409NotAServerError() throws Exception {
        // The email column is unique, so the second signup must surface as a conflict the
        // client can act on - not as a 500, which tells them nothing and pages someone.
        String body = """
                {"username":"first","email":"taken@example.com","password":"%s"}""".formatted(PASSWORD);

        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void aDuplicateEmailIsDetectedRegardlessOfCase() throws Exception {
        // users.email carries an ICU case-insensitive collation, so TAKEN@ and taken@ are the
        // same address as far as the unique constraint is concerned.
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"first","email":"mixed@example.com","password":"%s"}"""
                                .formatted(PASSWORD)))
                .andExpect(status().isOk());

        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"second","email":"MIXED@example.com","password":"%s"}"""
                                .formatted(PASSWORD)))
                .andExpect(status().isConflict());
    }

    // ---------- issuing ----------

    @Test
    void validCredentialsReturnAToken() throws Exception {
        mvc.perform(login(ACTIVE_EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.issuedAt").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    /**
     * The claims are the contract between the two halves of the feature: whatever generateToken
     * writes here is what JwtAuthenticationConverter has to be configured to read. Asserting them
     * directly means a rename on either side fails the build rather than producing a 403 with no
     * explanation.
     */
    @Test
    void theTokenCarriesTheUserIdAsSubjectAndTheRoleClaim() throws Exception {
        Jwt jwt = jwtDecoder.decode(tokenFor(ACTIVE_EMAIL));

        assertThat(jwt.getSubject()).isEqualTo(activeUserId.toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
    }

    @Test
    void theTokenExpiresFifteenMinutesAfterItWasIssued() throws Exception {
        Jwt jwt = jwtDecoder.decode(tokenFor(ACTIVE_EMAIL));

        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(java.time.Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toMinutes())
                .isEqualTo(15);
    }

    /**
     * The expiry reported in the body has to be the expiry inside the token. Computing it twice
     * from two separate calls to now() drifts them apart, and the client then treats a dead token
     * as live.
     */
    @Test
    void theReportedExpiryMatchesTheTokenItself() throws Exception {
        String body = mvc.perform(login(ACTIVE_EMAIL, PASSWORD))
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));

        assertThat(JsonPath.<String>read(body, "$.expiresAt"))
                .isEqualTo(jwt.getExpiresAt().toString());
        assertThat(JsonPath.<String>read(body, "$.issuedAt"))
                .isEqualTo(jwt.getIssuedAt().toString());
    }

    // ---------- rejecting ----------

    @Test
    void aWrongPasswordIsRejected() throws Exception {
        mvc.perform(login(ACTIVE_EMAIL, "not-the-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownEmailIsRejected() throws Exception {
        mvc.perform(login("nobody@example.com", PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The two failures above must be indistinguishable. Any difference - status, wording, even
     * response length - lets an attacker enumerate which addresses have accounts, which is the
     * first step of a credential-stuffing run. This is the assertion that rots silently, because
     * nothing else in the system notices when one branch grows a more helpful message.
     */
    @Test
    void anUnknownEmailAndAWrongPasswordAreIndistinguishable() throws Exception {
        var wrongPassword = mvc.perform(login(ACTIVE_EMAIL, "not-the-password")).andReturn();
        var unknownEmail = mvc.perform(login("nobody@example.com", PASSWORD)).andReturn();

        assertThat(unknownEmail.getResponse().getStatus())
                .isEqualTo(wrongPassword.getResponse().getStatus());
        assertThat(unknownEmail.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
    }

    /** The first thing that actually exercises AppUserDetails.isEnabled(). */
    @Test
    void aDisabledAccountCannotLogIn() throws Exception {
        mvc.perform(login(DISABLED_EMAIL, PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    // ---------- verifying ----------

    @Test
    void theIssuedTokenAuthenticatesAProtectedRequest() throws Exception {
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + tokenFor(ACTIVE_EMAIL)))
                .andExpect(status().isOk());
    }

    @Test
    void aRequestWithNoTokenIsRejected() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aGarbageTokenIsRejected() throws Exception {
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A token signed with a different key must be refused. Without this, "the signature is
     * checked" is an assumption rather than a tested property.
     */
    @Test
    void aTokenSignedWithAnotherKeyIsRejected() throws Exception {
        String foreignToken = new com.nimbusds.jwt.SignedJWT(
                new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),
                new com.nimbusds.jwt.JWTClaimsSet.Builder()
                        .subject(activeUserId.toString())
                        .claim("role", "ADMIN")
                        .expirationTime(new java.util.Date(System.currentTimeMillis() + 900_000))
                        .build()) {{
            sign(new com.nimbusds.jose.crypto.MACSigner(new byte[32]));
        }}.serialize();

        mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}""".formatted(email, password));
    }

    private String tokenFor(String email) throws Exception {
        String body = mvc.perform(login(email, PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
