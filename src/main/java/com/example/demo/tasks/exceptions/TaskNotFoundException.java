package com.example.demo.tasks.exceptions;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(String title) {
        super("Task with title " + title + " not found");
    }

    public TaskNotFoundException(int id) {
        super("Task with id " + id + " not found");
    }
}
