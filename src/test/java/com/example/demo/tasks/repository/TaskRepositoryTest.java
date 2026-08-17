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
 * ordering and case rules on search, its null-means-no-filter contract, the version bump, and
 * the optimistic-lock failure.
 * Turning an absent task into a 404 is no longer the store's job - it belongs to TaskService
 * and is covered by TaskApiTest.
 */
@SpringBootTest
@Import(PostgresTestContainer.class)
class TaskRepositoryTest {

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
        jdbc.execute("truncate table tasks restart identity");
    }

    @Test
    void saveAssignsAnIdAndTheInitialVersion() {
        Task saved = tx.execute(s -> repository.save(task("write tests", false)));

        assertNotNull(saved.getId(), "the store owns the id, so save must fill it in");
        assertEquals(0L, saved.getVersion(), "a freshly persisted task starts at version 0");
    }

    @Test
    void findByIdIsEmptyForAMissingRowRatherThanNull() {
        assertTrue(tx.execute(s -> repository.findById(404)).isEmpty());
    }

    @Test
    void findByIdReturnsWhatWasSaved() {
        Task saved = tx.execute(s -> repository.save(task("read me", true)));

        Task found = tx.execute(s -> repository.findById(saved.getId()).orElseThrow());

        assertEquals("read me", found.getTitle());
        assertTrue(found.isDone());
    }

    @Test
    void savingAnEditBumpsTheVersion() {
        Task saved = tx.execute(s -> repository.save(task("edit me", false)));

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
        Task saved = tx.execute(s -> repository.save(task("contended", false)));

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
        Task saved = tx.execute(s -> repository.save(task("delete me", false)));

        tx.execute(s -> {
            repository.delete(repository.findById(saved.getId()).orElseThrow());
            return null;
        });

        assertEquals(0, count());
        assertTrue(tx.execute(s -> repository.findById(saved.getId())).isEmpty());
    }

    // ---------- search ----------

    // One query serves every combination of the two filters, each switched off by a null
    // argument. The four tests below are the four combinations; between them they pin the
    // whole contract, because a predicate that stopped neutralising itself would break
    // exactly one of them.

    @Test
    void searchWithoutFiltersReturnsEverythingNewestFirst() {
        Instant now = Instant.now();
        tx.execute(s -> repository.save(task("oldest", false, now.minus(2, ChronoUnit.DAYS))));
        tx.execute(s -> repository.save(task("newest", true, now)));
        tx.execute(s -> repository.save(task("middle", false, now.minus(1, ChronoUnit.DAYS))));

        // Both done states come back: a null "done" must not collapse to false.
        assertEquals(List.of("newest", "middle", "oldest"),
                titles(tx.execute(s -> repository.search(null, null))));
    }

    @Test
    void searchByTitleIgnoresCase() {
        tx.execute(s -> repository.save(task("Buy Milk", false)));

        assertEquals(1, tx.execute(s -> repository.search("buy milk", null)).size());
        assertEquals(1, tx.execute(s -> repository.search("BUY MILK", null)).size());
        assertEquals(0, tx.execute(s -> repository.search("buy bread", null)).size());
    }

    @Test
    void searchByDoneSplitsTheTable() {
        tx.execute(s -> repository.save(task("finished", true)));
        tx.execute(s -> repository.save(task("outstanding", false)));

        assertEquals(List.of("finished"), titles(tx.execute(s -> repository.search(null, true))));
        assertEquals(List.of("outstanding"), titles(tx.execute(s -> repository.search(null, false))));
    }

    // The reason the three finders were merged: this combination was previously unaskable.
    @Test
    void searchAppliesTitleAndDoneTogether() {
        tx.execute(s -> repository.save(task("buy milk", true)));
        tx.execute(s -> repository.save(task("buy milk", false)));
        tx.execute(s -> repository.save(task("buy bread", false)));

        assertEquals(List.of("buy milk"), titles(tx.execute(s -> repository.search("buy milk", false))));
        assertEquals(List.of("buy milk"), titles(tx.execute(s -> repository.search("buy milk", true))));
        assertEquals(0, tx.execute(s -> repository.search("buy bread", true)).size());
    }

    // Search carries its own "order by created_at desc" rather than leaning on insertion order,
    // so this asserts the ordering survives when the two disagree.
    @Test
    void searchOrdersByCreatedAtNotByInsertion() {
        Instant now = Instant.now();
        tx.execute(s -> repository.save(task("stale", true, now.minus(1, ChronoUnit.DAYS))));
        tx.execute(s -> repository.save(task("fresh", true, now)));

        assertEquals(List.of("fresh", "stale"), titles(tx.execute(s -> repository.search(null, true))));
    }

    private Integer count() {
        return jdbc.queryForObject("select count(*) from tasks", Integer.class);
    }

    private static List<String> titles(List<Task> tasks) {
        return tasks.stream().map(Task::getTitle).toList();
    }

    private static Task task(String title, boolean done) {
        return task(title, done, Instant.now());
    }

    private static Task task(String title, boolean done, Instant createdAt) {
        return Task.builder()
                .title(title)
                .details("details")
                .createdAt(createdAt)
                .done(done)
                .build();
    }
}
