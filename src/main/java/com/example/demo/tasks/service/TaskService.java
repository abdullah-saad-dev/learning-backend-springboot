package com.example.demo.tasks.service;

import com.example.demo.tasks.dtos.UpdateTaskRequest;
import com.example.demo.tasks.entity.Task;
import com.example.demo.tasks.exceptions.TaskConflictException;
import com.example.demo.tasks.exceptions.TaskNotFoundException;
import com.example.demo.tasks.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskService {
    private final TaskRepository taskRepository;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    public Task findById(UUID id) {
        return read(id);
    }

    @Transactional
    public Task create(String title, String details, boolean done) {
        return taskRepository.save(Task.builder()
                .title(title)
                .details(details)
                .createdAt(clock.instant())
                .done(done)
                .build());
    }

    /**
     * Full replace: everything the caller can set is overwritten, id, ownerID and createdAt are not.
     */
    @Transactional
    public Task update(UUID id, UpdateTaskRequest r) {
        Task task = readAtVersion(id, r.version())
                .toBuilder()
                .title(r.title())
                .details(r.details())
                .done(r.done())
                .build();
        return taskRepository.save(task);
    }

    @Transactional
    public Task setDone(UUID id, UUID assigneeId, boolean done, long expectedVersion) {
        return taskRepository.save(readAtVersion(id, expectedVersion)
                .toBuilder()
                .done(done)
                .build()
        );
    }

    @Transactional
    public void delete(UUID id, long expectedVersion) {
        taskRepository.delete(readAtVersion(id, expectedVersion));
    }


    private Task read(UUID id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    private Task readAtVersion(UUID id, long expectedVersion) {
        Task current = read(id);
        if (current.getVersion() != expectedVersion)
            throw new TaskConflictException(id, expectedVersion, current.getVersion());
        return current;
    }

    public List<Task> search(String title, Boolean done) {
        return taskRepository.search(title, done);
    }
}
