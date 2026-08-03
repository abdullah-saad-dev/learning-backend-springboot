package com.example.demo.tasks;

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
 * The HTTP contract: status codes, media types and the version handshake. Runs the whole stack
 * against a real database, so a wrong status is caught here rather than by a client.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class TaskApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.execute("truncate table tasks restart identity");
    }

    // ---------- create ----------

    @Test
    void createReturns201WithALocationHeaderAndVersionZero() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"write tests","details":"with a container","done":false}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/tasks/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("write tests"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void createRejectsABlankTitle() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   ","details":"d","done":false}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void createRejectsAnOmittedDoneRatherThanDefaultingIt() throws Exception {
        // A missing "done" used to be coerced to false, silently marking the task pending.
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"no done","details":"d"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonIsA400NotA500() throws Exception {
        // Guards the catch-all exception handler: if it ever outranks Spring's own advice,
        // every one of these becomes a 500.
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---------- read ----------

    @Test
    void findByIdReturnsTheTask() throws Exception {
        int id = create("findable", false);

        mvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("findable"));
    }

    @Test
    void findByIdReturns404NotAServerError() throws Exception {
        mvc.perform(get("/api/tasks/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Task not found"));
    }

    @Test
    void listReturnsNewestFirst() throws Exception {
        create("first", false);
        create("second", false);
        create("third", false);

        mvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("third"))
                .andExpect(jsonPath("$[2].title").value("first"));
    }

    @Test
    void doneAndPendingFilterTheList() throws Exception {
        create("finished", true);
        create("outstanding", false);

        mvc.perform(get("/api/tasks/done"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("finished"));
        mvc.perform(get("/api/tasks/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("outstanding"));
    }

    @Test
    void searchByTitleIgnoresCase() throws Exception {
        create("Buy Milk", false);

        mvc.perform(get("/api/tasks").param("title", "buy milk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---------- update ----------

    @Test
    void putAtTheCurrentVersionSucceedsAndAdvancesIt() throws Exception {
        int id = create("before", false);

        mvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"after","details":"d","done":true,"version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("after"))
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void putAtAStaleVersionIsRejectedAsAConflict() throws Exception {
        int id = create("contended", false);
        editTitle(id, 0, "first writer wins");

        // Second writer still holds version 0. Without the check this would silently
        // overwrite the first edit.
        mvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"lost update","details":"d","done":false,"version":0}"""))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflicting change"));

        mvc.perform(get("/api/tasks/{id}", id))
                .andExpect(jsonPath("$.title").value("first writer wins"));
    }

    @Test
    void putWithoutAVersionIsRejected() throws Exception {
        int id = create("needs a version", false);

        mvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x","details":"d","done":true}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putToAMissingTaskIs404NotAConflict() throws Exception {
        mvc.perform(put("/api/tasks/{id}", 999).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x","details":"d","done":true,"version":0}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchMovesOnlyDone() throws Exception {
        int id = create("unchanged title", false);

        mvc.perform(patch("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":true,"version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("unchanged title"))
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void patchAtAStaleVersionIsRejectedAsAConflict() throws Exception {
        int id = create("contended", false);
        editTitle(id, 0, "moved on");

        mvc.perform(patch("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":true,"version":0}"""))
                .andExpect(status().isConflict());
    }

    // ---------- delete ----------

    @Test
    void deleteAtTheCurrentVersionReturns204AndTheTaskIsGone() throws Exception {
        int id = create("delete me", false);

        mvc.perform(delete("/api/tasks/{id}", id).param("version", "0"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAtAStaleVersionIsRejectedAsAConflict() throws Exception {
        int id = create("contended", false);
        editTitle(id, 0, "edited after you read it");

        mvc.perform(delete("/api/tasks/{id}", id).param("version", "0"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk());
    }

    @Test
    void deleteWithoutAVersionIsRejected() throws Exception {
        int id = create("needs a version", false);

        mvc.perform(delete("/api/tasks/{id}", id))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteOfAMissingTaskIs404() throws Exception {
        mvc.perform(delete("/api/tasks/{id}", 999).param("version", "0"))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    // JSON is written out by hand rather than serialised from the request records: the point of
    // these tests is the wire format, and building the body with the same mapper the server
    // parses it with would hide a mismatch instead of catching it.
    private int create(String title, boolean done) throws Exception {
        MvcResult result = mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","details":"details","done":%s}""".formatted(title, done)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    // Not named put(): a member method would shadow the statically imported request builder.
    private void editTitle(int id, long version, String title) throws Exception {
        mvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","details":"details","done":true,"version":%d}"""
                                .formatted(title, version)))
                .andExpect(status().isOk());
    }
}
