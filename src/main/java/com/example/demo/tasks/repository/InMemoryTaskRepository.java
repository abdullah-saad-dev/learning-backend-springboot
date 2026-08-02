package com.example.demo.tasks.repository;

import com.example.demo.tasks.Task;
import com.example.demo.tasks.exceptions.TaskNotFoundException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class InMemoryTaskRepository implements TaskRepository {
    // Reads run lock-free over the map and are weakly consistent; that is fine for GET.
    // Titles are no longer unique, so there is no cross-entry invariant left to protect and
    // every mutator can be expressed as one atomic ConcurrentHashMap operation - no lock.
    private final Map<Integer, Task> tasks = new ConcurrentHashMap<>();
    // Stands in for the IDENTITY column on TaskEntity: the store owns the id, not the caller.
    private final AtomicInteger nextId = new AtomicInteger(1);

    @Override
    public Task save(Task task) {
        Task stored = new Task(nextId.getAndIncrement(), task.title(), task.details(), task.createdAt(), task.done());
        tasks.put(stored.id(), stored);
        return stored;
    }

    @Override
    public void update(Task task) {
        // replace() only writes when the key is still present, so an update that races a
        // delete cannot resurrect the deleted task - it throws instead.
        if (tasks.replace(task.id(), task) == null)
            throw new TaskNotFoundException(task.id());
    }

    @Override
    public void delete(Task task) {
        // Idempotent: deleting an already-deleted task is not an error here. Callers that
        // need a 404 get it from the lookup they must do first to obtain the Task.
        tasks.remove(task.id());
    }

    @Override
    public Task findById(int id) {
        Task task = tasks.get(id);
        if (task == null)
            throw new TaskNotFoundException(id);
        return task;
    }

    @Override
    public List<Task> findByTitle(String title) {
        return tasks.values().stream()
                .filter(task -> task.title().equalsIgnoreCase(title))
                .toList();
    }

    @Override
    public List<Task> findByDone(boolean done) {
        return tasks.values().stream()
                .filter(task -> task.done() == done)
                .toList();
    }

    @Override
    public List<Task> findAll() {
        return List.copyOf(tasks.values());
    }
}
