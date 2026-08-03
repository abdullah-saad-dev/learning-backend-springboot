package com.example.demo.tasks.exceptions;

/**
 * The caller edited a version of the task that is no longer current: someone else wrote to it
 * in between. Distinct from "not found" - the task exists, the caller's copy is stale.
 */
public class TaskConflictException extends RuntimeException {
    public TaskConflictException(int id, Long expected, Long actual) {
        super("Task with id " + id + " has moved on: expected version "
                + expected + " but the stored version is " + actual);
    }
}
