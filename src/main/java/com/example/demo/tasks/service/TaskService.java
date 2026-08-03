package com.example.demo.tasks.service;

import com.example.demo.tasks.entity.TaskEntity;
import com.example.demo.tasks.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Owns what it means to create or change a task - stamping createdAt, defaulting an omitted
 * "done", and merging a partial edit onto the stored task. Takes plain values rather than the
 * web layer's request records, so it has no idea HTTP exists; the repository only stores.
 */
@Service
public class TaskService {
    private final TaskRepository tasks;
    @Autowired
    public TaskService(TaskRepository tasks) {
        this.tasks = tasks;
    }

    public List<TaskEntity> findAll() {
        return tasks.findAll();
    }

    public List<TaskEntity> findByTitle(String title) {
        return tasks.findByTitle(title);
    }

    public List<TaskEntity> findByDone(boolean done) {
        return tasks.findByDone(done);
    }

    public TaskEntity findById(int id) {
        return tasks.findById(id);
    }

    public TaskEntity create(String title, String details, Boolean done) {
        // No id: the store assigns it and hands the saved task back.
        return tasks.save(TaskEntity.builder()
                .title(title)
                .details(details)
                .createdAt(Instant.now())
                .done(Boolean.TRUE.equals(done))
                .build());
    }

    /** Full replace: everything the caller can set is overwritten, id and createdAt are not. */
    public TaskEntity update(int id, String title, String details, Boolean done) {
        return tasks.update(read(id).toBuilder()
                .title(title)
                .details(details)
                .done(Boolean.TRUE.equals(done))
                .build());
    }

    /** Partial edit: only "done" moves, the rest of the stored task is carried over. */
    public TaskEntity setDone(int id, boolean done) {
        return tasks.update(read(id).toBuilder()
                .done(done)
                .build());
    }

    public void delete(int id) {
        tasks.delete(read(id));
    }

    // Every edit reads the stored task first, so the fields the caller did not send survive.
    // That read and the write after it are two separate calls: a DELETE landing between them
    // would leave us writing a task that no longer exists, which the repository's update()
    // rejects rather than resurrecting. Two concurrent edits still resolve last-writer-wins.
    private TaskEntity read(int id) {
        return tasks.findById(id);
    }
}
