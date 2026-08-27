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
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskService {
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository tasks) {
        this.taskRepository = tasks;
    }

    public Task findByIdAndOwnerId(UUID id, UUID ownerId) {
        return read(id, ownerId);
    }

    @Transactional
    public Task create(UUID ownerId, String title, String details, boolean done) {
        return taskRepository.save(Task.builder()
                .title(title)
                .details(details)
                .createdAt(Instant.now())
                .done(done)
                .ownerId(ownerId)
                .build());
    }

    /**
     * Full replace: everything the caller can set is overwritten, id, ownerID and createdAt are not.
     */
    @Transactional
    public Task update(UUID id, UUID ownerId,UpdateTaskRequest r) {
        Task task = readAtVersion(id, ownerId, r.version())
                .toBuilder()
                .title(r.title())
                .details(r.details())
                .done(r.done())
                .build();
        return taskRepository.save(task);
    }

    @Transactional
    public Task setDone(UUID ownerId, UUID id, boolean done, long expectedVersion) {
        return taskRepository.save(readAtVersion(id, ownerId, expectedVersion)
                .toBuilder()
                .done(done)
                .build()
        );
    }

    @Transactional
    public void delete(UUID id, UUID ownerId, long expectedVersion) {
        taskRepository.delete(readAtVersion(id, ownerId, expectedVersion));
    }


    private Task read(UUID id, UUID ownerId) {
        return taskRepository.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new TaskNotFoundException(id));
    }

    private Task readAtVersion(UUID id, UUID ownerId, long expectedVersion) {
        Task current = read(id, ownerId);
        if (current.getVersion() != expectedVersion)
            throw new TaskConflictException(id, expectedVersion, current.getVersion());
        return current;
    }

    public List<Task> search(UUID ownerId, String title, Boolean done) {
        return taskRepository.search(title, ownerId, done);
    }
}
