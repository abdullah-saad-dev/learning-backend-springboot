package com.example.demo.tasks.repository;

import com.example.demo.PostgresTestContainer;
import com.example.demo.tasks.entity.TaskEntity;
import com.example.demo.tasks.exceptions.TaskNotFoundException;
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
 */
@SpringBootTest
@Import(PostgresTestContainer.class)
class JpaTaskRepositoryTest {

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
        TaskEntity saved = tx.execute(s -> repository.save(task("write tests", false)));

        assertNotNull(saved.getId(), "the store owns the id, so save must fill it in");
        assertEquals(0L, saved.getVersion(), "a freshly persisted task starts at version 0");
    }

    @Test
    void findByIdThrowsRatherThanReturningNull() {
        // The contract the in-memory store already honoured and this one used to break,
        // turning every 404 into a 500.
        assertThrows(TaskNotFoundException.class, () -> tx.execute(s -> repository.findById(404)));
    }

    @Test
    void findByIdReturnsWhatWasSaved() {
        TaskEntity saved = tx.execute(s -> repository.save(task("read me", true)));

        TaskEntity found = tx.execute(s -> repository.findById(saved.getId()));

        assertEquals("read me", found.getTitle());
        assertTrue(found.isDone());
    }

    @Test
    void updateBumpsTheVersion() {
        TaskEntity saved = tx.execute(s -> repository.save(task("edit me", false)));

        TaskEntity updated = tx.execute(s -> {
            TaskEntity current = repository.findById(saved.getId());
            return repository.update(current.toBuilder().title("edited").build());
        });

        assertEquals("edited", updated.getTitle());
        assertEquals(1L, updated.getVersion());
        assertEquals(1L, tx.execute(s -> repository.findById(saved.getId())).getVersion(),
                "the bump must be persisted, not just returned");
    }

    @Test
    void updateOfADeletedTaskIsRejectedRatherThanResurrectingIt() {
        TaskEntity saved = tx.execute(s -> repository.save(task("doomed", false)));
        tx.execute(s -> {
            repository.delete(repository.findById(saved.getId()));
            return null;
        });

        // em.merge() on a missing id would INSERT the row back; the store must refuse instead.
        assertThrows(TaskNotFoundException.class,
                () -> tx.execute(s -> repository.update(saved.toBuilder().title("back").build())));

        assertEquals(0, count(), "the deleted task was resurrected");
    }

    @Test
    void aCommitInsideTheTransactionWindowIsCaughtAtFlush() {
        TaskEntity saved = tx.execute(s -> repository.save(task("contended", false)));

        // The race the service's own version check cannot see: the version was still current
        // when this transaction read it, and stale by the time it flushed.
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> tx.execute(s -> {
            TaskEntity mine = repository.findById(saved.getId());

            newTx.execute(other -> {
                TaskEntity theirs = repository.findById(saved.getId());
                return repository.update(theirs.toBuilder().title("theirs").build());
            });

            return repository.update(mine.toBuilder().title("mine").build());
        }));

        assertEquals("theirs", tx.execute(s -> repository.findById(saved.getId())).getTitle(),
                "the losing write must not have landed");
    }

    @Test
    void deleteRemovesTheRow() {
        TaskEntity saved = tx.execute(s -> repository.save(task("delete me", false)));

        tx.execute(s -> {
            repository.delete(repository.findById(saved.getId()));
            return null;
        });

        assertEquals(0, count());
        assertThrows(TaskNotFoundException.class, () -> tx.execute(s -> repository.findById(saved.getId())));
    }

    @Test
    void findByTitleIgnoresCase() {
        tx.execute(s -> repository.save(task("Buy Milk", false)));

        assertEquals(1, tx.execute(s -> repository.findByTitle("buy milk")).size());
        assertEquals(1, tx.execute(s -> repository.findByTitle("BUY MILK")).size());
        assertEquals(0, tx.execute(s -> repository.findByTitle("buy bread")).size());
    }

    @Test
    void findByDoneSplitsTheTable() {
        tx.execute(s -> repository.save(task("finished", true)));
        tx.execute(s -> repository.save(task("outstanding", false)));

        assertEquals(List.of("finished"), titles(tx.execute(s -> repository.findByDone(true))));
        assertEquals(List.of("outstanding"), titles(tx.execute(s -> repository.findByDone(false))));
    }

    @Test
    void findAllReturnsNewestFirst() {
        Instant now = Instant.now();
        tx.execute(s -> repository.save(task("oldest", false, now.minus(2, ChronoUnit.DAYS))));
        tx.execute(s -> repository.save(task("newest", false, now)));
        tx.execute(s -> repository.save(task("middle", false, now.minus(1, ChronoUnit.DAYS))));

        assertEquals(List.of("newest", "middle", "oldest"), titles(tx.execute(s -> repository.findAll())));
    }

    private Integer count() {
        return jdbc.queryForObject("select count(*) from tasks", Integer.class);
    }

    private static List<String> titles(List<TaskEntity> tasks) {
        return tasks.stream().map(TaskEntity::getTitle).toList();
    }

    private static TaskEntity task(String title, boolean done) {
        return task(title, done, Instant.now());
    }

    private static TaskEntity task(String title, boolean done, Instant createdAt) {
        return TaskEntity.builder()
                .title(title)
                .details("details")
                .createdAt(createdAt)
                .done(done)
                .build();
    }
}
