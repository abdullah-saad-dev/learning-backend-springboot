package com.example.demo.tasks.exceptions;

import java.util.UUID;

/**
 * The requested assignee is not a member of the task's project. Rejected rather than
 * silently auto-adding them, because assignment must never be a back door into a project.
 */
public class InvalidAssigneeException extends RuntimeException {
    public InvalidAssigneeException(UUID assigneeId, UUID projectId) {
        super("User " + assigneeId + " is not a member of project " + projectId);
    }
}
