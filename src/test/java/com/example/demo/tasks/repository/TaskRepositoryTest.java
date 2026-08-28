package com.example.demo.tasks.repository;

import com.example.demo.PostgresTestContainer;
import com.example.demo.tasks.entity.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The store is exercised through real transactions rather than a test-managed one that rolls
 * back, because the behaviour worth testing here - the version check Hibernate performs at
 * flush - only happens when a transaction actually commits.
 * <p>
 * Spring Data supplies save/findById/delete, so those are not re-tested for their own sake.
 * What is asserted here is what this project relies on and derivation does not guarantee: the
 * ordering and case rules on search, its null-means-no-filter contract, the owner predicate that
 * is deliberately NOT optional, the version bump, and the optimistic-lock failure.
 * Turning an absent task into a 404 is not the store's job - it belongs to TaskService and is
 * covered by TaskApiTest.
 */
@SpringBootTest
@Import(PostgresTestContainer.class)
class TaskRepositoryTest {

    private static final UUID ALICE = UUID.fromString("01f11400-0000-7000-8000-00000000a11c");
    private static final UUID BOB = UUID.fromString("01f11400-0000-7000-8000-00000000b0b0");

    @Autowired
    private TaskRepository repository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private TransactionTemplate newTx;

    @BeforeEach
    void reset() {
        tx = new TransactionTemplate(transactionManager);
        newTx = new TransactionTemplate(transactionManager);
        // A genuinely separate transaction, so a test can commit a competing write in the
        // middle of another one without needing a second thread.
        newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // tasks.owner_id is a foreign key to users, so every task needs a real owner row and
        // truncating users cascades away their tasks in one statement.
        jdbc.execute("truncate table users cascade");
        insertUser(ALICE, "alice");
        insertUser(BOB, "bob");
    }

    @Test
    void saveAssignsAnIdAndTheInitialVersion() {
        Task saved = tx.execute(s -> repository.save(task(ALICE, "write tests", false)));

        assertNotNull(saved.getId(), "the store owns the id, so save must fill it in");
        assertEquals(0L, saved.getVersion(), "a freshly persisted task starts at version 0");
    }

    @Test
    void everySavedTaskGetsADistinctId() {
        // The id comes from Hibernate's @UuidGenerator rather than a sequence now, so this is
        // the check that replaced "the identity column counts up".
        Task first = tx.execute(s -> repository.save(task(ALICE, "one", false)));
        Task second = tx.execute(s -> repository.save(task(ALICE, "two", false)));

        assertTrue(!first.getId().equals(second.getId()), "two tasks must not share an id");
    }

    @Test
    void findByIdIsEmptyForAMissingRowRatherThanNull() {
        assertTrue(tx.execute(s -> repository.findById(UUID.randomUUID())).isEmpty());
    }

    @Test
    void findByIdReturnsWhatWasSaved() {
        Task saved = tx.execute(s -> repository.save(task(ALICE, "read me", true)));

        Task found = tx.execute(s -> repository.findById(saved.getId()).orElseThrow());

        assertEquals("read me", found.getTitle());
        assertTrue(found.isDone());
        assertEquals(ALICE, found.getOwnerId());
    }

    @Test
    void savingAnEditBumpsTheVersion() {
        Task saved = tx.execute(s -> repository.save(task(ALICE, "edit me", false)));

        Task updated = tx.execute(s -> {
            Task current = repository.findById(saved.getId()).orElseThrow();
            return repository.save(current.toBuilder().title("edited").build());
        });

        assertEquals("edited", updated.getTitle());
        assertEquals(1L, updated.getVersion());
        assertEquals(1L, tx.execute(s -> repository.findById(saved.getId()).orElseThrow()).getVersion(),
                "the bump must be persisted, not just returned");
    }

    @Test
    void aCommitInsideTheTransactionWindowIsCaughtAtFlush() {
        Task saved = tx.execute(s -> repository.save(task(ALICE, "contended", false)));

        // The race the service's own version check cannot see: the version was still current
        // when this transaction read it, and stale by the time it flushed.
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> tx.execute(s -> {
            Task mine = repository.findById(saved.getId()).orElseThrow();

            newTx.execute(other -> {
                Task theirs = repository.findById(saved.getId()).orElseThrow();
                return repository.save(theirs.toBuilder().title("theirs").build());
            });

            return repository.save(mine.toBuilder().title("mine").build());
        }));

        assertEquals("theirs", tx.execute(s -> repository.findById(saved.getId()).orElseThrow()).getTitle(),
                "the losing write must not have landed");
    }

    @Test
    void deleteRemovesTheRow() {
        Task saved = tx.execute(s -> repository.save(task(ALICE, "delete me", false)));

        tx.execute(s -> {
            repository.delete(repository.findById(saved.getId()).orElseThrow());
            return null;
        });

        assertEquals(0, count());
        assertTrue(tx.execute(s -> repository.findById(saved.getId())).isEmpty());
    }

    // ---------- ownership ----------

    @Test
    void findByIdAndOwnerIdReturnsTheOwnersOwnTask() {
        Task saved = tx.execute(s -> repository.save(task(ALICE, "hers", false)));

        assertTrue(tx.execute(s -> repository.findByIdAndOwnerId(saved.getId(), ALICE)).isPresent());
    }

    @Test
    void findByIdAndOwnerIdIsEmptyForAnotherOwnersTask() {
        // The row exists and the id is correct; only the owner is wrong. Empty here is what
        // becomes a 404 rather than a 403 one layer up.
        Task saved = tx.execute(s -> repository.save(task(ALICE, "hers", false)));

        assertTrue(tx.execute(s -> repository.findByIdAndOwnerId(saved.getId(), BOB)).isEmpty());
    }

    @Test
    void deletingAUserDeletesTheirTasks() {
        // ON DELETE CASCADE on the owner foreign key, asserted because it is a deliberate
        // choice in V4 rather than a default: a user's tasks are meaningless without them.
        tx.execute(s -> repository.save(task(BOB, "his", false)));
        tx.execute(s -> repository.save(task(ALICE, "hers", false)));

        jdbc.update("delete from users where id = ?", BOB);

        assertEquals(List.of("hers"), titles(tx.execute(s -> repository.search(null, ALICE, null))));
        assertEquals(1, count(), "only Bob's task should have gone");
    }

    // ---------- search ----------

    // One query serves every combination of the two optional filters, each switched off by a
    // null argument. The four tests below are the four combinations; between them they pin the
    // whole contract, because a predicate that stopped neutralising itself would break exactly
    // one of them. The owner predicate is tested separately, because it must NOT neutralise.

    @Test
    void searchWithoutFiltersReturnsEverythingNewestFirst() {
        Instant now = Instant.now();
        tx.execute(s -> repository.save(task(ALICE, "oldest", false, now.minus(2, ChronoUnit.DAYS))));
        tx.execute(s -> repository.save(task(ALICE, "newest", true, now)));
        tx.execute(s -> repository.save(task(ALICE, "middle", false, now.minus(1, ChronoUnit.DAYS))));

        // Both done states come back: a null "done" must not collapse to false.
        assertEquals(List.of("newest", "middle", "oldest"),
                titles(tx.execute(s -> repository.search(null, ALICE, null))));
    }

    @Test
    void searchByTitleIgnoresCase() {
        tx.execute(s -> repository.save(task(ALICE, "Buy Milk", false)));

        assertEquals(1, tx.execute(s -> repository.search("buy milk", ALICE, null)).size());
        assertEquals(1, tx.execute(s -> repository.search("BUY MILK", ALICE, null)).size());
        assertEquals(0, tx.execute(s -> repository.search("buy bread", ALICE, null)).size());
    }

    @Test
    void searchByDoneSplitsTheTable() {
        tx.execute(s -> repository.save(task(ALICE, "finished", true)));
        tx.execute(s -> repository.save(task(ALICE, "outstanding", false)));

        assertEquals(List.of("finished"), titles(tx.execute(s -> repository.search(null, ALICE, true))));
        assertEquals(List.of("outstanding"), titles(tx.execute(s -> repository.search(null, ALICE, false))));
    }

    // The reason the three finders were merged: this combination was previously unaskable.
    @Test
    void searchAppliesTitleAndDoneTogether() {
        tx.execute(s -> repository.save(task(ALICE, "buy milk", true)));
        tx.execute(s -> repository.save(task(ALICE, "buy milk", false)));
        tx.execute(s -> repository.save(task(ALICE, "buy bread", false)));

        assertEquals(List.of("buy milk"), titles(tx.execute(s -> repository.search("buy milk", ALICE, false))));
        assertEquals(List.of("buy milk"), titles(tx.execute(s -> repository.search("buy milk", ALICE, true))));
        assertEquals(0, tx.execute(s -> repository.search("buy bread", ALICE, true)).size());
    }

    // Unlike title and done, the owner predicate has no null-means-everything branch. If it ever
    // grows one, this is the test that fails - and it is the difference between a filter and a
    // security boundary.
    @Test
    void searchIsScopedToOneOwnerAndNeverWidens() {
        tx.execute(s -> repository.save(task(ALICE, "hers", false)));
        tx.execute(s -> repository.save(task(BOB, "his", false)));

        assertEquals(List.of("hers"), titles(tx.execute(s -> repository.search(null, ALICE, null))));
        assertEquals(List.of("his"), titles(tx.execute(s -> repository.search(null, BOB, null))));
        assertEquals(0, tx.execute(s -> repository.search("hers", BOB, null)).size(),
                "a title that matches another owner's task must not widen the scope");
    }

    // Search carries its own "order by created_at desc" rather than leaning on insertion order,
    // so this asserts the ordering survives when the two disagree. It matters more now that ids
    // are UUIDs: there is no ascending primary key left to accidentally sort by.
    @Test
    void searchOrdersByCreatedAtNotByInsertion() {
        Instant now = Instant.now();
        tx.execute(s -> repository.save(task(ALICE, "stale", true, now.minus(1, ChronoUnit.DAYS))));
        tx.execute(s -> repository.save(task(ALICE, "fresh", true, now)));

        assertEquals(List.of("fresh", "stale"), titles(tx.execute(s -> repository.search(null, ALICE, true))));
    }

    // ---------- helpers ----------

    private void insertUser(UUID id, String name) {
        jdbc.update("""
                insert into users (id, username, email, password_hash, role, enabled)
                values (?, ?, ?, 'x', 'USER', true)""", id, name, name + "@example.com");
    }

    private static Task task(UUID ownerId, String title, boolean done) {
        return task(ownerId, title, done, Instant.now());
    }

    private static Task task(UUID ownerId, String title, boolean done, Instant createdAt) {
        return Task.builder()
                .title(title)
                .details("details")
                .done(done)
                .ownerId(ownerId)
                .createdAt(createdAt)
                .build();
    }

    private Integer count() {
        return jdbc.queryForObject("select count(*) from tasks", Integer.class);
    }

    private static List<String> titles(List<Task> tasks) {
        return tasks.stream().map(Task::getTitle).toList();
    }
}
