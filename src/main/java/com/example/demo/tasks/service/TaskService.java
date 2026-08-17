package com.example.demo.tasks.service;

import com.example.demo.tasks.dtos.UpdateTaskRequest;
import com.example.demo.tasks.entity.Task;
import com.example.demo.tasks.exceptions.TaskConflictException;
import com.example.demo.tasks.exceptions.TaskNotFoundException;
import com.example.demo.tasks.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TaskService {
    private final TaskRepository tasks;

    public TaskService(TaskRepository tasks) {
        this.tasks = tasks;
    }

    public Task findById(int id) {
        return read(id);
    }

    @Transactional
    public Task create(String title, String details, boolean done) {
        return tasks.save(Task.builder()
                .title(title)
                .details(details)
                .createdAt(Instant.now())
                .done(done)
                .build());
    }

    /**
     * Full replace: everything the caller can set is overwritten, id and createdAt are not.
     */
    @Transactional
    public Task update(int id, UpdateTaskRequest r) {
        Task task = readAtVersion(id, r.version())
                .toBuilder()
                .title(r.title())
                .details(r.details())
                .done(r.done())
                .build();
        return tasks.save(task);
    }

    @Transactional
    public Task setDone(int id, boolean done, long expectedVersion) {
        return tasks.save(readAtVersion(id, expectedVersion)
                .toBuilder()
                .done(done)
                .build()
        );
    }

    @Transactional
    public void delete(int id, long expectedVersion) {
        tasks.delete(readAtVersion(id, expectedVersion));
    }


    private Task read(int id) {
        return tasks.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    private Task readAtVersion(int id, long expectedVersion) {
        Task current = read(id);
        if (current.getVersion() != expectedVersion)
            throw new TaskConflictException(id, expectedVersion, current.getVersion());
        return current;
    }

    public List<Task> search(String title, Boolean done) {
        return tasks.search(title, done);
    }
}
