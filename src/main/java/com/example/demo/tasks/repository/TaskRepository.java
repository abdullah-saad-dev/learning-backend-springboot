package com.example.demo.tasks.repository;

import com.example.demo.tasks.Task;

import java.util.List;

public interface TaskRepository {

    // Returns the stored task: the id is assigned by the store, so the caller cannot know it
    // until save() has run.
    Task save(Task task);

    void delete(Task task);

    void update(Task task);

    Task findById(int id);

    List<Task> findByTitle(String title);

    List<Task> findByDone(boolean done);

    List<Task> findAll();
}
