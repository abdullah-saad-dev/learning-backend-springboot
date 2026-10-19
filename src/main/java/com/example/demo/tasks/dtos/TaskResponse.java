package com.example.demo.tasks.dtos;

import com.example.demo.auth.entity.User;
import com.example.demo.tasks.entity.Task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the API returns. Keeping this separate from TaskEntity means the JSON contract is not
 * hostage to the database schema: a new column, a renamed field or a lazy association cannot
 * change the response, and nothing about persistence leaks to clients.
 * <p>
 * "version" is the exception: it is deliberately published because a client cannot edit safely
 * without it. Read it here, send it back on the next PUT or PATCH.
 * <p>
 * "assigneeId" is the only part of the assignee published: the client already has the
 * project's member list (GET /api/projects/{id}/members) to resolve it to a name.
 */
public record TaskResponse(UUID id, UUID projectId, String title, String details, LocalDate dueDate,
                           UUID assigneeId, Instant createdAt, boolean done, long version) {

    public static TaskResponse of(Task task) {
        UUID projectId = task.getProject() != null ? task.getProject().getId() : null;
        User assignee = task.getAssignee();
        return new TaskResponse(task.getId(), projectId, task.getTitle(), task.getDetails(),
                task.getDueDate(), assignee != null ? assignee.getId() : null,
                task.getCreatedAt(), task.isDone(), task.getVersion());
    }

    public static List<TaskResponse> of(List<Task> tasks) {
        return tasks.stream().map(TaskResponse::of).toList();
    }
}
