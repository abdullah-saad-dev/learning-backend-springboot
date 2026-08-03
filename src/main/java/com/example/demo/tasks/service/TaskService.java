package com.example.demo.tasks.service;

import com.example.demo.tasks.entity.TaskEntity;
import com.example.demo.tasks.exceptions.TaskConflictException;
import com.example.demo.tasks.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Owns what it means to create or change a task - stamping createdAt, defaulting an omitted
 * "done", and merging a partial edit onto the stored task. Takes plain values rather than the
 * web layer's request records, so it has no idea HTTP exists; the repository only stores.
 * <p>
 * It also owns the transaction boundary. A service call is the unit of work, so a read and the
 * write that depends on it commit or roll back together; the repository opens nothing itself.
 */
@Service
@Transactional(readOnly = true)
public class TaskService {
    private final TaskRepository tasks;

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

    @Transactional
    public TaskEntity create(String title, String details, boolean done) {
        // No id: the store assigns it and hands the saved task back.
        return tasks.save(TaskEntity.builder()
                .title(title)
                .details(details)
                .createdAt(Instant.now())
                .done(done)
                .build());
    }

    /** Full replace: everything the caller can set is overwritten, id and createdAt are not. */
    @Transactional
    public TaskEntity update(int id, String title, String details, boolean done, long expectedVersion) {
        return tasks.update(readAtVersion(id, expectedVersion).toBuilder()
                .title(title)
                .details(details)
                .done(done)
                .build());
    }

    /** Partial edit: only "done" moves, the rest of the stored task is carried over. */
    @Transactional
    public TaskEntity setDone(int id, boolean done, long expectedVersion) {
        return tasks.update(readAtVersion(id, expectedVersion).toBuilder()
                .done(done)
                .build());
    }

    // Version-checked like the edits: deleting on the strength of a copy that has since been
    // changed by someone else destroys the change they made without either side noticing.
    @Transactional
    public void delete(int id, long expectedVersion) {
        tasks.delete(readAtVersion(id, expectedVersion));
    }

    // Every edit reads the stored task first, so the fields the caller did not send survive.
    // That read and the write after it now share one transaction, so a DELETE landing between
    // them cannot be missed: the repository's update() rejects the write rather than
    // resurrecting the task.
    private TaskEntity read(int id) {
        return tasks.findById(id);
    }

    /**
     * Two independent checks, because they catch different races and neither covers the other:
     * <ul>
     *   <li>this one, against the version the caller read in an <em>earlier</em> request - the
     *       common case, where two people edit the same task from stale copies minutes apart;</li>
     *   <li>the store's own @Version check at flush, against a writer that commits inside this
     *       transaction's window - narrow, but the one this cannot see.</li>
     * </ul>
     * Without the first, every edit would still be last-writer-wins over HTTP no matter what
     * the column does.
     */
    private TaskEntity readAtVersion(int id, long expectedVersion) {
        TaskEntity current = read(id);
        if (current.getVersion() != expectedVersion)
            throw new TaskConflictException(id, expectedVersion, current.getVersion());
        return current;
    }
}
