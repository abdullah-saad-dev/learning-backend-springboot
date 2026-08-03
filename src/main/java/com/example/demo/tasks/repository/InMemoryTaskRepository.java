package com.example.demo.tasks.repository;

import com.example.demo.tasks.entity.TaskEntity;
import com.example.demo.tasks.exceptions.TaskNotFoundException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class InMemoryTaskRepository implements TaskRepository {
    // Reads run lock-free over the map and are weakly consistent; that is fine for GET.
    // Titles are not unique, so there is no cross-entry invariant to protect and every
    // mutator is a single atomic ConcurrentHashMap operation - no lock needed.
    private final Map<Integer, TaskEntity> tasks = new ConcurrentHashMap<>();
    // Stands in for the IDENTITY column on TaskEntity: the store owns the id, not the caller.
    private final AtomicInteger nextId = new AtomicInteger(1);

    @Override
    public TaskEntity save(TaskEntity task) {
        TaskEntity stored = task.toBuilder()
                .id(nextId.getAndIncrement())
                .build();
        tasks.put(stored.getId(), stored);
        return copy(stored);
    }

    @Override
    public TaskEntity update(TaskEntity task) {
        TaskEntity stored = copy(task);
        // replace() only writes when the key is still present, so an update that races a
        // delete cannot resurrect the deleted task - it throws instead. Note replace()
        // returns the *previous* value, so the new state is returned from `stored`.
        if (tasks.replace(stored.getId(), stored) == null)
            throw new TaskNotFoundException(stored.getId());
        return copy(stored);
    }

    @Override
    public void delete(TaskEntity task) {
        // Idempotent: deleting an already-deleted task is not an error here. Callers that
        // need a 404 get it from the lookup they must do first to obtain the task.
        tasks.remove(task.getId());
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
        return tasks.values().stream()
                .filter(task -> task.getTitle().equalsIgnoreCase(title))
                .map(this::copy)
                .toList();
    }

    @Override
    public List<TaskEntity> findByDone(boolean done) {
        return tasks.values().stream()
                .filter(task -> task.isDone() == done)
                .map(this::copy)
                .toList();
    }

    @Override
    public List<TaskEntity> findAll() {
        return tasks.values().stream()
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
