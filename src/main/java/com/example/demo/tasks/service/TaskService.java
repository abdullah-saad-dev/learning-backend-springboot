package com.example.demo.tasks.service;

import com.example.demo.audit.AuditEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ApplicationEventPublisher eventPublisher;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository,
                       UserRepository userRepository, Clock clock, AppSecurityContext securityContext,
                       ApplicationEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.securityContext = securityContext;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Loads a single task visible to the current user, or throws {@link TaskNotFoundException}.
     */
    public Task findById(UUID id) {
        return read(id);
    }

    /**
     * Creates a new task inside a project the current user belongs to, verifies that any assignee
     * is also a member of that project, and publishes a {@code TASK_CREATED} event.
     */
    @Transactional
    public Task create(UUID projectId, String title, String details, boolean done,
                       LocalDate dueDate, UUID assigneeId) {
        UUID userId = securityContext.getCurrentUserId();
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ProjectNotFoundException("no project with id " + projectId));

        Task saved = taskRepository.saveAndFlush(Task.builder()
                .project(project)
                .title(title)
                .details(details)
                .createdAt(clock.instant())
                .done(done)
                .dueDate(dueDate)
                .assignee(resolveAssignee(projectId, assigneeId))
                .build());
        eventPublisher.publishEvent(new AuditEvent(
                "TASK_CREATED", "TASK", saved.getId(), userId, taskChanges(saved)));
        return saved;
    }

    /**
     * Replaces a task's mutable fields after checking its optimistic-lock version and publishes
     * a {@code TASK_UPDATED} event (including {@code previousAssigneeId} for assignment notifications).
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @taskSecurity.canModify(#id)")
    public Task update(UUID id, UpdateTaskRequest r) {
        Task task = read(id);
        if (r.version() == null || !Objects.equals(task.getVersion(), r.version())) {
            throw new TaskConflictException(id, r.version(), task.getVersion());
        }
        UUID previousAssigneeId = task.getAssignee() != null ? task.getAssignee().getId() : null;
        task.setTitle(r.title());
        task.setDetails(r.details());
        task.setDone(r.done());
        task.setDueDate(r.dueDate());
        task.setAssignee(resolveAssignee(task.getProject().getId(), r.assigneeId()));
        Task saved = taskRepository.saveAndFlush(task);
        Map<String, Object> changes = taskChanges(saved);
        if (previousAssigneeId != null) {
            changes.put("previousAssigneeId", previousAssigneeId.toString());
        }
        eventPublisher.publishEvent(new AuditEvent(
                "TASK_UPDATED", "TASK", saved.getId(), securityContext.getCurrentUserId(), changes));
        return saved;
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

    /**
     * Toggles a task's completion status at the expected optimistic-lock version and publishes
     * a {@code TASK_STATUS_CHANGED} event.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @taskSecurity.canModify(#id)")
    public Task setDone(UUID id, boolean done, long expectedVersion) {
        Task task = read(id);
        if (!Objects.equals(task.getVersion(), expectedVersion)) {
            throw new TaskConflictException(id, expectedVersion, task.getVersion());
        }
        task.setDone(done);
        Task saved = taskRepository.saveAndFlush(task);
        eventPublisher.publishEvent(new AuditEvent(
                "TASK_STATUS_CHANGED", "TASK", saved.getId(), securityContext.getCurrentUserId(), taskChanges(saved)));
        return saved;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @taskSecurity.canModify(#id)")
    public Task setDone(UUID id, UUID projectId, boolean done, long expectedVersion) {
        return setDone(id, done, expectedVersion);
    }

    /**
     * Deletes a task at the expected optimistic-lock version and publishes a {@code TASK_DELETED} event.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @taskSecurity.isProjectManager(#id)")
    public void delete(UUID id, long version) {
        Task task = read(id);
        if (!Objects.equals(task.getVersion(), version)) {
            throw new TaskConflictException(id, version, task.getVersion());
        }
        Map<String, Object> changes = taskChanges(task);
        taskRepository.delete(task);
        eventPublisher.publishEvent(new AuditEvent(
                "TASK_DELETED", "TASK", id, securityContext.getCurrentUserId(), changes));
    }

    /**
     * Builds a snapshot map of a task's key properties for audit logging and event listeners.
     */
    private Map<String, Object> taskChanges(Task task) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (task.getProject() != null && task.getProject().getId() != null) {
            changes.put("projectId", task.getProject().getId().toString());
        }
        changes.put("title", task.getTitle());
        changes.put("done", task.isDone());
        if (task.getDueDate() != null) {
            changes.put("dueDate", task.getDueDate().toString());
        }
        if (task.getAssignee() != null && task.getAssignee().getId() != null) {
            changes.put("assigneeId", task.getAssignee().getId().toString());
        }
        return changes;
    }

    /**
     * Internal helper that fetches a task scoped to projects the current user belongs to,
     * throwing {@link TaskNotFoundException} (404) if missing or inaccessible.
     */
    private Task read(UUID id) {
        UUID userId = securityContext.getCurrentUserId();
        return taskRepository.findByIdAndUserId(id, userId).orElseThrow(() -> new TaskNotFoundException(id));
    }

    /**
     * Searches all tasks visible to the current user matching optional title and completion filters.
     */
    public List<Task> search(String title, Boolean done) {
        UUID userId = securityContext.getCurrentUserId();
        return taskRepository.search(userId, title, done);
    }

    /**
     * Searches a single page of tasks visible to the current user matching optional title and completion filters.
     */
    @Transactional(readOnly = true)
    public Page<Task> search(String title, Boolean done, Pageable pageable) {
        UUID userId = securityContext.getCurrentUserId();
        return taskRepository.search(userId, title, done, pageable);
    }
}
