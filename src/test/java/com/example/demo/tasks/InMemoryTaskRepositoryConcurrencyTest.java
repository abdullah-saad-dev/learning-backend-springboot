package com.example.demo.tasks;

import com.example.demo.tasks.entity.TaskEntity;
import com.example.demo.tasks.exceptions.TaskConflictException;
import com.example.demo.tasks.exceptions.TaskNotFoundException;
import com.example.demo.tasks.repository.InMemoryTaskRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryTaskRepositoryConcurrencyTest {
    private static final int ROUNDS = 2000;

    /**
     * A PUT racing a DELETE, both built from the same version. Exactly one may land, and the
     * store must agree with whichever did:
     * <ul>
     *   <li>delete first - the update finds no entry and throws TaskNotFoundException; the
     *       store ends empty and the task is <em>not</em> resurrected;</li>
     *   <li>update first - the delete is working from a version that no longer exists and is
     *       refused, leaving the edit in place at version 1.</li>
     * </ul>
     * The second case is new: before the version column the delete would have gone through and
     * silently destroyed the edit.
     */
    @Test
    void updateRacingDeleteLeavesTheStoreAgreeingWithTheWinner() throws Exception {
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                TaskEntity task = repository.save(TaskEntity.builder()
                        .title("task")
                        .details("original")
                        .createdAt(Instant.now())
                        .done(false)
                        .build());
                CyclicBarrier barrier = new CyclicBarrier(2);

                Future<Boolean> update = pool.submit(() -> {
                    barrier.await();
                    try {
                        repository.update(task.toBuilder().details("edited").done(true).build());
                        return true;
                    } catch (TaskNotFoundException expected) {
                        return false; // delete won the race
                    }
                });
                Future<Boolean> delete = pool.submit(() -> {
                    barrier.await();
                    try {
                        repository.delete(task);
                        return true;
                    } catch (TaskConflictException expected) {
                        return false; // update won the race, so this copy is stale
                    }
                });
                boolean updated = update.get();
                boolean deleted = delete.get();

                assertNotEquals(updated, deleted, "round " + round + ": both operations landed");
                if (deleted) {
                    // Asserted against this task rather than findAll(): a round whose update
                    // wins leaves a survivor behind, so the store is not empty by round two.
                    assertThrows(TaskNotFoundException.class, () -> repository.findById(task.getId()),
                            "round " + round + ": deleted task was resurrected by the racing update");
                } else {
                    TaskEntity survivor = repository.findById(task.getId());
                    assertEquals("edited", survivor.getDetails(),
                            "round " + round + ": the winning edit was destroyed by the stale delete");
                    assertEquals(1L, survivor.getVersion(), "round " + round + ": version did not advance");
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Two edits built from the same version race. Exactly one may win: the loser's version is
     * stale by the time it lands, so it must be rejected rather than silently overwriting the
     * winner. Without the version check both would succeed and one edit would vanish.
     */
    @Test
    void concurrentEditsFromTheSameVersionCannotBothWin() throws Exception {
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                TaskEntity task = repository.save(TaskEntity.builder()
                        .title("task")
                        .details("original")
                        .createdAt(Instant.now())
                        .done(false)
                        .build());
                CyclicBarrier barrier = new CyclicBarrier(2);
                AtomicInteger wins = new AtomicInteger();
                AtomicInteger conflicts = new AtomicInteger();

                List<Future<Object>> edits = Stream.of("a", "b").map(edit -> pool.submit(() -> {
                    barrier.await();
                    try {
                        // Both start from the version they read, exactly as two HTTP clients would.
                        repository.update(task.toBuilder().details(edit).build());
                        wins.incrementAndGet();
                    } catch (TaskConflictException expected) {
                        conflicts.incrementAndGet();
                    }
                    return null;
                })).toList();
                for (Future<?> edit : edits) edit.get();

                assertEquals(1, wins.get(), "round " + round + ": both edits were accepted");
                assertEquals(1, conflicts.get(), "round " + round + ": the stale edit was not rejected");
                assertEquals(1L, repository.findById(task.getId()).getVersion(),
                        "round " + round + ": version did not advance exactly once");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * TaskEntity is mutable, so the store must never hand out a reference into its own map -
     * otherwise a caller's setter, or a half-applied edit, is visible to every other thread.
     */
    @Test
    void storedTasksAreNotReachableByCallers() {
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        TaskEntity saved = repository.save(TaskEntity.builder()
                .title("task")
                .details("original")
                .createdAt(Instant.now())
                .done(false)
                .build());

        assertNotSame(saved, repository.findById(saved.getId()));

        // Mutating what save() returned must not reach into the store.
        saved.setTitle("mutated");
        assertEquals("task", repository.findById(saved.getId()).getTitle());

        // Nor must mutating what a finder returned.
        repository.findById(saved.getId()).setTitle("mutated again");
        assertEquals("task", repository.findById(saved.getId()).getTitle());
    }
}
