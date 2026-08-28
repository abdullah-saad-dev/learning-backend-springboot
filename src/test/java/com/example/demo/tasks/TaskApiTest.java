package com.example.demo.tasks;

import com.example.demo.MockMvcSecurity;
import com.example.demo.PostgresTestContainer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract: status codes, media types, the version handshake, and - since tasks became
 * owned - the ownership boundary. Runs the whole stack against a real database, so a wrong status
 * is caught here rather than by a client.
 * <p>
 * Requests are authenticated with the {@code jwt()} post-processor rather than {@code @WithMockUser}.
 * That is not a style choice: the controller resolves {@code @AuthenticationPrincipal Jwt}, and
 * {@code @WithMockUser} installs a UsernamePasswordAuthenticationToken whose principal is a plain
 * String - so the Jwt argument would resolve to null and every handler would NPE into a 500. The
 * subject claim is the user's UUID, matching what JwtConfig sets as the principal claim name.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainer.class, MockMvcSecurity.class})
class TaskApiTest {

    // Fixed rather than random so a failure names who was who.
    private static final UUID ALICE = UUID.fromString("01f11400-0000-7000-8000-00000000a11c");
    private static final UUID BOB = UUID.fromString("01f11400-0000-7000-8000-00000000b0b0");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        // tasks.owner_id is a foreign key to users, so tasks cannot be seeded without owners,
        // and truncating users cascades away their tasks in one statement.
        jdbc.execute("truncate table users cascade");
        insertUser(ALICE, "alice");
        insertUser(BOB, "bob");
    }

    // ---------- create ----------

    @Test
    void createReturns201WithALocationHeaderAndVersionZero() throws Exception {
        MvcResult result = mvc.perform(post("/api/tasks").with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"write tests","details":"with a container","done":false}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("write tests"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(header().exists("Location"))
                .andReturn();

        // The id is server-owned and now a UUID, so Location is asserted against whatever the
        // server chose rather than against a number the test could predict.
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertDoesNotThrow(() -> UUID.fromString(id), "the published id must be a UUID");
        assertEquals("/api/tasks/" + id, result.getResponse().getHeader("Location"));
    }

    @Test
    void createStoresTheCallerAsTheOwnerRatherThanTrustingTheBody() throws Exception {
        // The body names Bob. The owner must still be the authenticated caller: ownership comes
        // from the token and is not an input, so a client cannot write a task into someone
        // else's account by asking nicely.
        String id = create(ALICE, "mine", false, """
                {"title":"mine","details":"details","done":false,"ownerId":"%s"}""".formatted(BOB));

        String owner = jdbc.queryForObject(
                "select owner_id::text from tasks where id = ?::uuid", String.class, id);
        assertEquals(ALICE.toString(), owner);
    }

    @Test
    void createRejectsABlankTitle() throws Exception {
        mvc.perform(post("/api/tasks").with(as(ALICE)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   ","details":"d","done":false}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void createRejectsAnOmittedDoneRatherThanDefaultingIt() throws Exception {
        // A missing "done" used to be coerced to false, silently marking the task pending.
        mvc.perform(post("/api/tasks").with(as(ALICE)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"no done","details":"d"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonIsA400NotA500() throws Exception {
        // Guards the catch-all exception handler: if it ever outranks Spring's own advice,
        // every one of these becomes a 500.
        mvc.perform(post("/api/tasks").with(as(ALICE)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---------- read ----------

    @Test
    void findByIdReturnsTheTask() throws Exception {
        String id = create(ALICE, "findable", false);

        mvc.perform(get("/api/tasks/{id}", id).with(as(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("findable"));
    }

    @Test
    void findByIdReturns404NotAServerError() throws Exception {
        mvc.perform(get("/api/tasks/{id}", UUID.randomUUID()).with(as(ALICE)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Task not found"));
    }

    @Test
    void anUnparseableIdIsA400NotA500() throws Exception {
        // The path variable is a UUID now, so a client can hand the binder something that is not
        // one. That must reach Spring's own 400, not fall through to the catch-all as a 500.
        mvc.perform(get("/api/tasks/{id}", "not-a-uuid").with(as(ALICE)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void listReturnsNewestFirst() throws Exception {
        create(ALICE, "first", false);
        create(ALICE, "second", false);
        create(ALICE, "third", false);

        mvc.perform(get("/api/tasks").with(as(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("third"))
                .andExpect(jsonPath("$[2].title").value("first"));
    }

    // "done" is boxed so it has three states, not two: absent means no filter at all, which is
    // what replaced the old /done and /pending routes.
    @Test
    void theDoneParameterFiltersTheListAndOmittingItDoesNot() throws Exception {
        create(ALICE, "finished", true);
        create(ALICE, "outstanding", false);

        mvc.perform(get("/api/tasks").with(as(ALICE)).param("done", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("finished"));
        mvc.perform(get("/api/tasks").with(as(ALICE)).param("done", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("outstanding"));
        mvc.perform(get("/api/tasks").with(as(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void searchByTitleIgnoresCase() throws Exception {
        create(ALICE, "Buy Milk", false);

        mvc.perform(get("/api/tasks").with(as(ALICE)).param("title", "buy milk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void titleAndDoneNarrowTheListTogether() throws Exception {
        String milk = create(ALICE, "buy milk", false);
        create(ALICE, "buy bread", false);
        markDone(ALICE, milk, 0);

        mvc.perform(get("/api/tasks").with(as(ALICE)).param("title", "buy milk").param("done", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/tasks").with(as(ALICE)).param("title", "buy milk").param("done", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // A query parameter Spring cannot bind must reach its own 400, not fall through to the
    // catch-all handler and surface as a 500.
    @Test
    void anUnparseableDoneParameterIsA400NotA500() throws Exception {
        mvc.perform(get("/api/tasks").with(as(ALICE)).param("done", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---------- the ownership boundary ----------

    // Every one of these answers 404 rather than 403. A 403 would confirm the row exists, which
    // hands a caller an enumeration oracle over other people's data - the same reasoning that
    // makes all five refresh-token failure branches return an identical 401.

    @Test
    void anotherOwnersTaskIsNotFound() throws Exception {
        String hers = create(ALICE, "alice's secret", false);

        mvc.perform(get("/api/tasks/{id}", hers).with(as(BOB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Task not found"));
    }

    @Test
    void theListContainsOnlyTheCallersOwnTasks() throws Exception {
        create(ALICE, "alice one", false);
        create(ALICE, "alice two", false);
        create(BOB, "bob one", false);

        mvc.perform(get("/api/tasks").with(as(BOB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("bob one"));
    }

    @Test
    void aTitleSearchDoesNotLeakAnotherOwnersTasks() throws Exception {
        create(ALICE, "buy milk", false);

        // Bob guesses a title that exists - for someone else. The owner predicate is not
        // optional, so the filter cannot widen the result past his own rows.
        mvc.perform(get("/api/tasks").with(as(BOB)).param("title", "buy milk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anotherOwnersTaskCannotBeReplaced() throws Exception {
        String hers = create(ALICE, "alice's task", false);

        mvc.perform(put("/api/tasks/{id}", hers).with(as(BOB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"stolen","details":"d","done":true,"version":0}"""))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/tasks/{id}", hers).with(as(ALICE)))
                .andExpect(jsonPath("$.title").value("alice's task"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void anotherOwnersTaskCannotBePatched() throws Exception {
        String hers = create(ALICE, "alice's task", false);

        mvc.perform(patch("/api/tasks/{id}/done", hers).with(as(BOB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":true,"version":0}"""))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/tasks/{id}", hers).with(as(ALICE)))
                .andExpect(jsonPath("$.done").value(false));
    }

    @Test
    void anotherOwnersTaskCannotBeDeleted() throws Exception {
        String hers = create(ALICE, "alice's task", false);

        mvc.perform(delete("/api/tasks/{id}", hers).with(as(BOB)).param("version", "0"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/tasks/{id}", hers).with(as(ALICE)))
                .andExpect(status().isOk());
    }

    // ---------- set done ----------

    @Test
    void patchMovesOnlyDone() throws Exception {
        String id = create(ALICE, "unchanged title", false);

        mvc.perform(patch("/api/tasks/{id}/done", id).with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":true,"version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("unchanged title"))
                .andExpect(jsonPath("$.details").value("details"))
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void patchAtAStaleVersionIsRejectedAsAConflict() throws Exception {
        String id = create(ALICE, "contended", false);
        markDone(ALICE, id, 0);

        // Second writer still holds version 0. Without the check this would silently
        // overwrite the first write.
        mvc.perform(patch("/api/tasks/{id}/done", id).with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":false,"version":0}"""))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflicting change"));

        mvc.perform(get("/api/tasks/{id}", id).with(as(ALICE)))
                .andExpect(jsonPath("$.done").value(true));
    }

    @Test
    void patchWithoutAVersionIsRejected() throws Exception {
        String id = create(ALICE, "needs a version", false);

        mvc.perform(patch("/api/tasks/{id}/done", id).with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":true}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchToAMissingTaskIs404NotAConflict() throws Exception {
        mvc.perform(patch("/api/tasks/{id}/done", UUID.randomUUID()).with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":true,"version":0}"""))
                .andExpect(status().isNotFound());
    }

    // ---------- full replace ----------

    // PUT carries the whole task in the body, version included - same spelling as PATCH.
    // DELETE has no body, so it takes the version as a query parameter instead.
    @Test
    void putAtTheCurrentVersionSucceedsAndAdvancesIt() throws Exception {
        String id = create(ALICE, "before", false);

        mvc.perform(put("/api/tasks/{id}", id).with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"after","details":"edited","done":true,"version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("after"))
                .andExpect(jsonPath("$.details").value("edited"))
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void putAtAStaleVersionIsRejectedAsAConflict() throws Exception {
        String id = create(ALICE, "contended", false);
        markDone(ALICE, id, 0);

        mvc.perform(put("/api/tasks/{id}", id).with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"lost update","details":"d","done":false,"version":0}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicting change"));

        mvc.perform(get("/api/tasks/{id}", id).with(as(ALICE)))
                .andExpect(jsonPath("$.title").value("contended"));
    }

    @Test
    void putWithoutAVersionIsRejected() throws Exception {
        String id = create(ALICE, "needs a version", false);

        mvc.perform(put("/api/tasks/{id}", id).with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x","details":"d","done":true}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putToAMissingTaskIs404NotAConflict() throws Exception {
        mvc.perform(put("/api/tasks/{id}", UUID.randomUUID()).with(as(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x","details":"d","done":true,"version":0}"""))
                .andExpect(status().isNotFound());
    }

    // ---------- delete ----------

    @Test
    void deleteAtTheCurrentVersionReturns204AndTheTaskIsGone() throws Exception {
        String id = create(ALICE, "delete me", false);

        mvc.perform(delete("/api/tasks/{id}", id).with(as(ALICE)).param("version", "0"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/tasks/{id}", id).with(as(ALICE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAtAStaleVersionIsRejectedAsAConflict() throws Exception {
        String id = create(ALICE, "contended", false);
        markDone(ALICE, id, 0);

        mvc.perform(delete("/api/tasks/{id}", id).with(as(ALICE)).param("version", "0"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/tasks/{id}", id).with(as(ALICE)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteWithoutAVersionIsRejected() throws Exception {
        String id = create(ALICE, "needs a version", false);

        mvc.perform(delete("/api/tasks/{id}", id).with(as(ALICE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteOfAMissingTaskIs404() throws Exception {
        mvc.perform(delete("/api/tasks/{id}", UUID.randomUUID()).with(as(ALICE)).param("version", "0"))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    /**
     * Authenticates as one user. JwtConfig sets "sub" as the principal claim name, so the
     * subject here is exactly what TaskController reads back as the owner id.
     */
    private static RequestPostProcessor as(UUID userId) {
        return jwt().jwt(builder -> builder.subject(userId.toString()));
    }

    private void insertUser(UUID id, String name) {
        jdbc.update("""
                insert into users (id, username, email, password_hash, role, enabled)
                values (?, ?, ?, 'x', 'USER', true)""", id, name, name + "@example.com");
    }

    // JSON is written out by hand rather than serialised from the request records: the point of
    // these tests is the wire format, and building the body with the same mapper the server
    // parses it with would hide a mismatch instead of catching it.
    private String create(UUID owner, String title, boolean done) throws Exception {
        return create(owner, title, done, """
                {"title":"%s","details":"details","done":%s}""".formatted(title, done));
    }

    private String create(UUID owner, String title, boolean done, String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/tasks").with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void markDone(UUID owner, String id, long version) throws Exception {
        mvc.perform(patch("/api/tasks/{id}/done", id).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":true,"version":%d}""".formatted(version)))
                .andExpect(status().isOk());
    }
}
