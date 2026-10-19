package com.example.demo.tasks.service;

import com.example.demo.auth.AppSecurityContext;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.repository.UserRepository;
import com.example.demo.project.Project;
import com.example.demo.project.ProjectRepository;
import com.example.demo.project.exceptions.ProjectNotFoundException;
import com.example.demo.tasks.dtos.UpdateTaskRequest;
import com.example.demo.tasks.entity.Task;
import com.example.demo.tasks.exceptions.InvalidAssigneeException;
import com.example.demo.tasks.exceptions.TaskConflictException;
import com.example.demo.tasks.exceptions.TaskNotFoundException;
import com.example.demo.tasks.repository.TaskRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskService {
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final AppSecurityContext securityContext;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository,
                       UserRepository userRepository, Clock clock, AppSecurityContext securityContext) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.securityContext = securityContext;
    }

    public Task findById(UUID id) {
        return read(id);
    }

    @Transactional
    public Task create(UUID projectId, String title, String details, boolean done,
                       LocalDate dueDate, UUID assigneeId) {
        UUID userId = securityContext.getCurrentUserId();
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ProjectNotFoundException("no project with id " + projectId));

        Task task = Task.builder()
                .project(project)
                .title(title)
                .details(details)
                .createdAt(clock.instant())
                .done(done)
                .dueDate(dueDate)
                .build();
        task.setAssignee(resolveAssignee(projectId, assigneeId));
        return taskRepository.save(task);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @taskSecurity.canModify(#id)")
    public Task update(UUID id, UpdateTaskRequest r) {
        Task task = read(id);
        if (r.version() == null || !Objects.equals(task.getVersion(), r.version())) {
            throw new TaskConflictException(id, r.version(), task.getVersion());
        }
        task.setTitle(r.title());
        task.setDetails(r.details());
        task.setDone(r.done());
        task.setDueDate(r.dueDate());
        task.setAssignee(resolveAssignee(task.getProject().getId(), r.assigneeId()));
        return taskRepository.save(task);
    }

    /**
     * null means unassigned. Otherwise the user must already belong to the project: checked
     * here rather than trusted from the client, because assigning someone a task would
     * otherwise let them read it without ever being let into the project.
     */
    private User resolveAssignee(UUID projectId, UUID assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        if (!projectRepository.isMember(projectId, assigneeId)) {
            throw new InvalidAssigneeException(assigneeId, projectId);
        }
        return userRepository.getReferenceById(assigneeId);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @taskSecurity.canModify(#id)")
    public Task setDone(UUID id, boolean done, long expectedVersion) {
        Task task = read(id);
        if (!Objects.equals(task.getVersion(), expectedVersion)) {
            throw new TaskConflictException(id, expectedVersion, task.getVersion());
        }
        task.setDone(done);
        return taskRepository.save(task);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @taskSecurity.canModify(#id)")
    public Task setDone(UUID id, UUID projectId, boolean done, long expectedVersion) {
        return setDone(id, done, expectedVersion);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @taskSecurity.isProjectManager(#id)")
    public void delete(UUID id, long version) {
        Task task = read(id);
        if (!Objects.equals(task.getVersion(), version)) {
            throw new TaskConflictException(id, version, task.getVersion());
        }
        taskRepository.delete(task);
    }

    private Task read(UUID id) {
        UUID userId = securityContext.getCurrentUserId();
        return taskRepository.findByIdAndUserId(id, userId).orElseThrow(() -> new TaskNotFoundException(id));
    }

    public List<Task> search(String title, Boolean done) {
        UUID userId = securityContext.getCurrentUserId();
        return taskRepository.search(userId, title, done);
    }
}
