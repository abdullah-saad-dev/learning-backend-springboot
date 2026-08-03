package com.example.demo.tasks.repository;

import com.example.demo.tasks.entity.TaskEntity;
import com.example.demo.tasks.exceptions.TaskConflictException;
import com.example.demo.tasks.exceptions.TaskNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Test and local-only store, active only under the "inmemory" profile so it is not a live bean
 * in production.
 */
@Repository
@Profile("inmemory")
public class InMemoryTaskRepository implements TaskRepository {
    // Reads run lock-free over the map and are weakly consistent; that is fine for GET.
    // Titles are not unique, so there is no cross-entry invariant to protect and every
    // mutator is a single atomic ConcurrentHashMap operation - no lock needed.
    private final Map<Integer, TaskEntity> tasks = new ConcurrentHashMap<>();
    // Stands in for the IDENTITY column on TaskEntity: the store owns the id, not the caller.
    private final AtomicInteger nextId = new AtomicInteger(1);

    // Newest first, matching the JPA store's "order by createdAt desc". Id breaks ties so a
    // window is stable across calls even when two tasks share a timestamp.
    private static final Comparator<TaskEntity> NEWEST_FIRST =
            Comparator.comparing(TaskEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(TaskEntity::getId, Comparator.reverseOrder());

    @Override
    public TaskEntity save(TaskEntity task) {
        // Stands in for @Version's initial value, which Hibernate sets on persist.
        TaskEntity stored = task.toBuilder()
                .id(nextId.getAndIncrement())
                .version(0L)
                .build();
        tasks.put(stored.getId(), stored);
        return copy(stored);
    }

    @Override
    public TaskEntity update(TaskEntity task) {
        // computeIfPresent is atomic per key, so the version check and the write that depends
        // on it cannot be split by a racing writer - this is the in-memory equivalent of
        // "UPDATE ... WHERE id = ? AND version = ?". Absent key means no remapping runs, so an
        // update racing a delete still cannot resurrect the task. Throwing from inside the
        // function leaves the entry untouched.
        TaskEntity stored = tasks.computeIfPresent(task.getId(), (id, current) -> {
            if (!Objects.equals(current.getVersion(), task.getVersion()))
                throw new TaskConflictException(id, task.getVersion(), current.getVersion());
            return task.toBuilder()
                    .version(current.getVersion() + 1)
                    .build();
        });
        if (stored == null)
            throw new TaskNotFoundException(task.getId());
        return copy(stored);
    }

    @Override
    public TaskEntity delete(TaskEntity task) {
        // Returning null from computeIfPresent removes the entry, so the version check and the
        // removal are one atomic step - remove(key) alone would drop whatever is there,
        // including a newer version written since the caller read. remove(key, value) is not
        // an option: TaskEntity's equals is id-based, so it would match any version.
        // Still idempotent for an absent key: no remapping runs and nothing is thrown. Callers
        // that need a 404 get it from the lookup they must do first to obtain the task.
        tasks.computeIfPresent(task.getId(), (id, current) -> {
            if (!Objects.equals(current.getVersion(), task.getVersion()))
                throw new TaskConflictException(id, task.getVersion(), current.getVersion());
            return null;
        });
        return task;
    }

    @Override
    public TaskEntity findById(int id) {
        TaskEntity task = tasks.get(id);
        if (task == null)
            throw new TaskNotFoundException(id);
        return copy(task);
    }

    @Override
    public List<TaskEntity> findByTitle(String title) {
        return sortedCopies(tasks.values().stream()
                .filter(task -> task.getTitle().equalsIgnoreCase(title)));
    }

    @Override
    public List<TaskEntity> findByDone(boolean done) {
        return sortedCopies(tasks.values().stream()
                .filter(task -> task.isDone() == done));
    }

    @Override
    public List<TaskEntity> findAll() {
        return sortedCopies(tasks.values().stream());
    }

    private List<TaskEntity> sortedCopies(Stream<TaskEntity> tasks) {
        return tasks.sorted(NEWEST_FIRST)
                .map(this::copy)
                .toList();
    }

    /**
     * TaskEntity has setters, so handing out a stored instance would let any caller mutate
     * the store - and let a reader observe a half-written task. Copying on the way in and on
     * the way out keeps every stored instance effectively immutable, which is what makes the
     * lock-free design above safe.
     */
    private TaskEntity copy(TaskEntity task) {
        return task.toBuilder().build();
    }
}
