package com.example.demo.tasks;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(String title) {
        super("Task with title " + title + " not found");
    }
}
