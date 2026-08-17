package com.example.demo.tasks.dtos;

import com.example.demo.tasks.entity.Task;

import java.time.Instant;
import java.util.List;

/**
 * What the API returns. Keeping this separate from TaskEntity means the JSON contract is not
 * hostage to the database schema: a new column, a renamed field or a lazy association cannot
 * change the response, and nothing about persistence leaks to clients.
 * <p>
 * "version" is the exception: it is deliberately published because a client cannot edit safely
 * without it. Read it here, send it back on the next PUT or PATCH.
 */
public record TaskResponse(int id, String title, String details, Instant createdAt, boolean done,
                           long version) {

    public static TaskResponse of(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDetails(),
                task.getCreatedAt(),   task.isDone(), task.getVersion());
    }

    public static List<TaskResponse> of(List<Task> tasks) {
        return tasks.stream().map(TaskResponse::of).toList();
    }
}
