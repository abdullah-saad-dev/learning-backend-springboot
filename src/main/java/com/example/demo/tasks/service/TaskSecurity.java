package com.example.demo.tasks.service;

import com.example.demo.auth.AppSecurityContext;
import com.example.demo.tasks.exceptions.TaskNotFoundException;
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
        UUID currentUserId = requireVisibleTask(taskId);
        return taskRepository.isUserAssignedToTask(taskId, currentUserId);
    }

    public boolean isProjectManager(UUID taskId) {
        UUID currentUserId = requireVisibleTask(taskId);
        return taskRepository.isProjectManagerForTask(taskId, currentUserId);
    }

    public boolean canModify(UUID taskId) {
        UUID currentUserId = requireVisibleTask(taskId);
        return taskRepository.isUserAssignedToTask(taskId, currentUserId)
                || taskRepository.isProjectManagerForTask(taskId, currentUserId);
    }

    /**
     * Ensures that non-existent tasks and tasks belonging to projects the caller is not a
     * member of surface as 404 Not Found rather than 403 Forbidden (avoiding an existence
     * oracle before TaskService.read is reached).
     */
    private UUID requireVisibleTask(UUID taskId) {
        if (taskId == null) {
            throw new TaskNotFoundException(null);
        }
        UUID currentUserId = securityContext.getCurrentUserId();
        if (currentUserId == null || taskRepository.findByIdAndUserId(taskId, currentUserId).isEmpty()) {
            throw new TaskNotFoundException(taskId);
        }
        return currentUserId;
    }
}
