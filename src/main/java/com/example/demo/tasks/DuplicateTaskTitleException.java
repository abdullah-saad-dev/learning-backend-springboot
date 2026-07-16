package com.example.demo.tasks;

public class DuplicateTaskTitleException extends RuntimeException {
    public DuplicateTaskTitleException(String title) {
        super("Task with title " + title + " already exists");
    }
}
