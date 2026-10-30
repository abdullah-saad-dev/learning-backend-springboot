package com.example.demo.project;

import com.example.demo.MockMvcSecurity;
import com.example.demo.PostgresTestContainer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainer.class, MockMvcSecurity.class})
class ProjectApiTest {

    private static final UUID ALICE = UUID.fromString("01f11400-0000-7000-8000-00000000a11c");
    private static final UUID BOB = UUID.fromString("01f11400-0000-7000-8000-00000000b0b0");
    private static final UUID CHARLIE = UUID.fromString("01f11400-0000-7000-8000-00000000c0c0");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.execute("truncate table audit_logs, projects, users cascade");
        insertUser(ALICE, "alice");
        insertUser(BOB, "bob");
        insertUser(CHARLIE, "charlie");
    }

    @Test
    void createReturns201WithProjectLocationAndMakesCallerManager() throws Exception {
        MvcResult result = mvc.perform(post("/api/projects").with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Core Platform","description":"v1","priority":"HIGH","status":"IN_PROGRESS",
                                 "startDate":"2026-10-01","dueDate":"2026-11-01"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Core Platform"))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(header().exists("Location"))
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertEquals("/api/projects/" + id, result.getResponse().getHeader("Location"));
    }

    @Test
    void createRejectsDueDateEarlierThanStartDateAs400() throws Exception {
        mvc.perform(post("/api/projects").with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Inverted","priority":"LOW","status":"PLANNED",
                                 "startDate":"2026-11-10","dueDate":"2026-10-01"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void anotherUsersProjectIs404Never403() throws Exception {
        String id = createProject(ALICE, "Secret");

        mvc.perform(get("/api/projects/{id}", id).with(as(BOB)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/projects/{id}", id).with(as(BOB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tampered","priority":"HIGH","status":"IN_PROGRESS","version":0}"""))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/projects/{id}", id).with(as(BOB)).param("version", "0"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/{id}/activity", id).with(as(BOB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateBumpsVersionAndRejectsStaleWritesWith409() throws Exception {
        String id = createProject(ALICE, "Initial");

        mvc.perform(put("/api/projects/{id}", id).with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed","description":"updated","priority":"URGENT",
                                 "status":"IN_PROGRESS","version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(put("/api/projects/{id}", id).with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Stale overwrite","priority":"LOW","status":"COMPLETED","version":0}"""))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void memberCanReadProjectButCannotUpdateOrDeleteIt() throws Exception {
        String id = createProject(ALICE, "Shared");

        mvc.perform(post("/api/projects/{id}/members", id).with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bob@example.com","role":"MEMBER"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BOB.toString()))
                .andExpect(jsonPath("$.role").value("MEMBER"));

        mvc.perform(get("/api/projects/{id}", id).with(as(BOB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.memberCount").value(2));

        mvc.perform(put("/api/projects/{id}", id).with(as(BOB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Unauthorized edit","priority":"LOW","status":"PLANNED","version":0}"""))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/projects/{id}", id).with(as(BOB)).param("version", "0"))
                .andExpect(status().isForbidden());
    }

    @Test
    void soleManagerCannotBeRemovedOrDemoted() throws Exception {
        String id = createProject(ALICE, "Solo Managed");

        mvc.perform(delete("/api/projects/{id}/members/{userId}", id, ALICE).with(as(ALICE)))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/projects/{id}/members/{userId}/role", id, ALICE).with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void activityEndpointReturnsRecordedProjectEvents() throws Exception {
        String id = createProject(ALICE, "Audited");

        mvc.perform(post("/api/projects/{id}/members", id).with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"MEMBER"}""".formatted(CHARLIE)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/projects/{id}/activity", id).with(as(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].action", hasItem("PROJECT_CREATED")))
                .andExpect(jsonPath("$[*].action", hasItem("MEMBER_ADDED")));
    }

    private String createProject(UUID owner, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/projects").with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"desc","priority":"MEDIUM","status":"IN_PROGRESS",
                                 "startDate":"2026-10-01","dueDate":"2026-11-01"}""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static RequestPostProcessor as(UUID userId) {
        return jwt().jwt(builder -> builder.subject(userId.toString()));
    }

    private void insertUser(UUID id, String name) {
        jdbc.update("""
                insert into users (id, username, email, password_hash, role, enabled)
                values (?, ?, ?, 'x', 'USER', true)""", id, name, name + "@example.com");
    }
}
