package com.example.demo.tasks;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TaskService {
    // Reads run lock-free over the map and are weakly consistent; that is fine for GET.
    // Every mutator is synchronized because "no two tasks share a title" is an invariant
    // across entries, which no single-key ConcurrentHashMap operation can make atomic.
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    public List<Task> findAll() {
        return List.copyOf(tasks.values());
    }

    public Task findByTitle(String title) {
        return single(title);
    }

    public List<Task> findByDone(boolean done) {
        return tasks.values().stream()
                .filter(task -> task.done() == done)
                .toList();
    }

    public synchronized Task create(String title, String details, Boolean done) {
        if (exists(title))
            throw new DuplicateTaskTitleException(title);
        String id = UUID.randomUUID().toString();
        Task task = new Task(id, title, details, Instant.now(), done != null && done);
        tasks.put(id, task);
        return task;
    }

    public synchronized Task deleteByTitle(String title) {
        Task task = single(title);
        tasks.remove(task.id());
        return task;
    }

    public synchronized Task update(String title, String newTitle, String details, Boolean done) {
        Task current = single(title);
        // A rename may not collide with a different task.
        if (!current.title().equalsIgnoreCase(newTitle) && exists(newTitle))
            throw new DuplicateTaskTitleException(newTitle);
        Task updated = new Task(current.id(), newTitle, details, current.createdAt(), done != null && done);
        tasks.put(current.id(), updated);
        return updated;
    }

    public synchronized Task setDone(String title, boolean done) {
        Task current = single(title);
        Task updated = new Task(current.id(), current.title(), current.details(), current.createdAt(), done);
        tasks.put(current.id(), updated);
        return updated;
    }

    private Task single(String title) {
        return tasks.values().stream()
                .filter(task -> task.title().equalsIgnoreCase(title))
                .findFirst()
                .orElseThrow(() -> new TaskNotFoundException(title));
    }

    private boolean exists(String title) {
        return tasks.values().stream()
                .anyMatch(task -> task.title().equalsIgnoreCase(title));
    }
}
