package com.example.demo.tasks;

import com.example.demo.tasks.entity.TaskEntity;

import java.time.Instant;
import java.util.List;

/**
 * What the API returns. Keeping this separate from TaskEntity means the JSON contract is not
 * hostage to the database schema: a new column, a renamed field or a lazy association cannot
 * change the response, and nothing about persistence leaks to clients.
 */
public record TaskResponse(int id, String title, String details, Instant createdAt, boolean done) {

    public static TaskResponse of(TaskEntity task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDetails(),
                task.getCreatedAt(), task.isDone());
    }

    public static List<TaskResponse> of(List<TaskEntity> tasks) {
        return tasks.stream().map(TaskResponse::of).toList();
    }
}
