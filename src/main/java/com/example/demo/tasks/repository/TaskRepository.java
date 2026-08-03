package com.example.demo.tasks.repository;

import com.example.demo.tasks.entity.TaskEntity;
import com.example.demo.tasks.exceptions.TaskNotFoundException;

import java.util.List;

/**
 * The store behind the tasks API.
 * <p>
 * Two implementations exist and they previously disagreed about the absent-row case, which the
 * compiler could not catch because nothing here said what it should be. It does now: <b>every
 * lookup of a specific task throws {@link TaskNotFoundException} when there is no such row -
 * none of them return null.</b> Callers may rely on that and skip null checks.
 * <p>
 * List methods return every match, ordered newest-first.
 */
public interface TaskRepository {

    // Returns the stored task: the id is assigned by the store, so the caller cannot know it
    // until save() has run.
    TaskEntity save(TaskEntity task);

    /**
     * Removes the task only if its stored version still matches the one carried by the argument.
     *
     * @throws com.example.demo.tasks.exceptions.TaskConflictException if it has moved on.
     */
    TaskEntity delete(TaskEntity task);

    /**
     * @throws TaskNotFoundException if the task no longer exists (e.g. a racing delete won).
     * @throws com.example.demo.tasks.exceptions.TaskConflictException if the stored version has
     *         moved past the one carried by the argument.
     */
    TaskEntity update(TaskEntity task);

    /** @throws TaskNotFoundException if no task has this id. */
    TaskEntity findById(int id);

    List<TaskEntity> findByTitle(String title);

    List<TaskEntity> findByDone(boolean done);

    List<TaskEntity> findAll();
}
