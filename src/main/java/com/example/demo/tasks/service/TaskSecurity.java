package com.example.demo.tasks.service;

import com.example.demo.auth.AppSecurityContext;
import com.example.demo.tasks.repository.TaskRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("taskSecurity")
public class TaskSecurity {
    private final TaskRepository taskRepository;
    private final AppSecurityContext securityContext;

    public TaskSecurity(TaskRepository taskRepository, AppSecurityContext securityContext) {
        this.taskRepository = taskRepository;
        this.securityContext = securityContext;
    }

    public boolean isAssignee(UUID taskId) {
        if (taskId == null) {
            return false;
        }
        UUID currentUserId = securityContext.getCurrentUserId();
        if (currentUserId == null) {
            return false;
        }
        return taskRepository.isUserAssignedToTask(taskId, currentUserId);
    }

    public boolean isProjectManager(UUID taskId) {
        if (taskId == null) {
            return false;
        }
        UUID currentUserId = securityContext.getCurrentUserId();
        if (currentUserId == null) {
            return false;
        }
        return taskRepository.isProjectManagerForTask(taskId, currentUserId);
    }

    public boolean canModify(UUID taskId) {
        return isAssignee(taskId) || isProjectManager(taskId);
    }
}
