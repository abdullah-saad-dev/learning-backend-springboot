package com.example.demo.tasks.repository;

import com.example.demo.tasks.entity.TaskEntity;

import java.util.List;

public interface TaskRepository {

    // Returns the stored task: the id is assigned by the store, so the caller cannot know it
    // until save() has run.
    TaskEntity save(TaskEntity task);

    void delete(TaskEntity task);

    TaskEntity update(TaskEntity task);

    TaskEntity findById(int id);

    List<TaskEntity> findByTitle(String title);

    List<TaskEntity> findByDone(boolean done);

    List<TaskEntity> findAll();
}
