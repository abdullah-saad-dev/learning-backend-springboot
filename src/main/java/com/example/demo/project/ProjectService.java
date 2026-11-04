package com.example.demo.project;

import com.example.demo.audit.AuditEvent;
import com.example.demo.auth.AppSecurityContext;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.repository.UserRepository;
import com.example.demo.project.dtos.CreateProjectRequest;
import com.example.demo.project.dtos.ProjectMemberResponse;
import com.example.demo.project.dtos.UpdateProjectRequest;
import com.example.demo.project.enums.ProjectPriority;
import com.example.demo.project.enums.ProjectRole;
import com.example.demo.project.enums.ProjectStatus;
import com.example.demo.project.exceptions.ProjectConflictException;
import com.example.demo.project.exceptions.ProjectNotFoundException;
import com.example.demo.tasks.repository.TaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final Clock clock;
    private final AppSecurityContext securityContext;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectService(ProjectRepository projectRepository, TaskRepository taskRepository,
                          Clock clock, AppSecurityContext securityContext, UserRepository userRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.clock = clock;
        this.securityContext = securityContext;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Loads a project visible to the currently authenticated user, or throws {@link ProjectNotFoundException}.
     */
    @Transactional(readOnly = true)
    public Project findById(UUID id) {
        return read(id);
    }

    /**
     * Searches projects the current user belongs to, optionally filtered by name, status, and priority.
     */
    @Transactional(readOnly = true)
    public List<Project> search(String name, ProjectStatus status, ProjectPriority priority) {
        UUID userId = securityContext.getCurrentUserId();
        List<Project> readProjects = projectRepository.search(name, userId, status, priority);
        log.atDebug()
                .addKeyValue("projectsIds", readProjects.stream().map(Project::getId).toList())
                .setMessage("projects read")
                .log();
        return readProjects;
    }

    /**
     * Internal helper that fetches a project scoped to the current user's membership,
     * throwing {@link ProjectNotFoundException} (404) if missing or inaccessible.
     */
    private Project read(UUID id) {
        UUID userId = securityContext.getCurrentUserId();
        Project readProject = projectRepository.findByIdAndUserId(id, userId).orElseThrow(
                () -> {
                    log.atDebug()
                            .addKeyValue("userId", userId)
                            .addKeyValue("projectId", id)
                            .setMessage("user is not a member of the project")
                            .log();
                    return new ProjectNotFoundException("no project with id " + id);
                });
        log.atDebug()
                .addKeyValue("projectId", readProject.getId())
                .setMessage("project read")
                .log();
        return readProject;
    }

    /**
     * Creates a new project with the current user as {@code MANAGER}, validates start/due dates,
     * and publishes a {@code PROJECT_CREATED} event.
     */
    @Transactional
    public Project create(CreateProjectRequest r) {
        User current = userRepository.getReferenceById(securityContext.getCurrentUserId());
        Map<User, ProjectRole> members = new HashMap<>();
        if (r.members() != null) {
            for (Map.Entry<UUID, ProjectRole> entry : r.members().entrySet()) {
                if (entry.getKey() != null && !entry.getKey().equals(current.getId())) {
                    userRepository.findById(entry.getKey()).ifPresent(user ->
                            members.put(user, entry.getValue() != null ? entry.getValue() : ProjectRole.MEMBER)
                    );
                }
            }
        }
        members.put(current, ProjectRole.MANAGER);
        LocalDate startDate = r.startDate() != null ? r.startDate() : LocalDate.now(clock);
        LocalDate dueDate = r.dueDate() != null ? r.dueDate() : startDate.plusDays(30);
        if (dueDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Due date cannot be earlier than start date");
        }
        Project project = Project.builder()
                .name(r.name())
                .createdAt(clock.instant())
                .description(r.description())
                .dueDate(dueDate)
                .startDate(startDate)
                .priority(r.priority())
                .status(r.status())
                .membersWithRole(members)
                .build();
        Project savedProject = projectRepository.save(project);
        eventPublisher.publishEvent(new AuditEvent(
                "PROJECT_CREATED", "PROJECT", savedProject.getId(), current.getId(), projectChanges(savedProject)));
        log.atInfo()
                .addKeyValue("projectId", savedProject.getId())
                .addKeyValue("managerId", current.getId())
                .setMessage("project created")
                .log();
        return savedProject;
    }

    /**
     * Deletes a project at the expected optimistic-lock version and publishes a {@code PROJECT_DELETED} event.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @projectSecurity.isManager(#id)")
    public void delete(UUID id, long expectedVersion) {
        int deletedRows = projectRepository.delete(id, expectedVersion);
        UUID userId = securityContext.getCurrentUserId();
        if (deletedRows == 0) {
            throw new ProjectConflictException("the project changed while viewing, please refresh the page and try again");
        }
        eventPublisher.publishEvent(new AuditEvent(
                "PROJECT_DELETED", "PROJECT", id, userId, Map.of("version", expectedVersion)));
        log.atInfo()
                .addKeyValue("projectId", id)
                .setMessage("project deleted")
                .addKeyValue("userId", userId)
                .log();
    }

    /**
     * Updates a project's attributes after verifying its optimistic-lock version and date ordering.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @projectSecurity.isManager(#r.id())")
    public Project update(UpdateProjectRequest r) {
        UUID userId = securityContext.getCurrentUserId();
        Project project = read(r.id());
        if (r.version() != null && project.getVersion() != r.version()) {
            throw new ProjectConflictException("the project changed while viewing, please refresh the page and try again");
        }
        if (r.name() != null) project.setName(r.name());
        if (r.description() != null) project.setDescription(r.description());
        if (r.dueDate() != null) project.setDueDate(r.dueDate());
        if (r.startDate() != null) project.setStartDate(r.startDate());
        if (r.priority() != null) project.setPriority(r.priority());
        if (r.status() != null) project.setStatus(r.status());
        if (project.getDueDate() != null && project.getStartDate() != null
                && project.getDueDate().isBefore(project.getStartDate())) {
            throw new IllegalArgumentException("Due date cannot be earlier than start date");
        }

        Project saved = projectRepository.saveAndFlush(project);
        eventPublisher.publishEvent(new AuditEvent(
                "PROJECT_UPDATED", "PROJECT", saved.getId(), userId, projectChanges(saved)));
        log.atInfo()
                .addKeyValue("projectId", r.id())
                .setMessage("project updated")
                .addKeyValue("userId", userId)
                .log();
        return saved;
    }

    /**
     * Lists all members and their assigned roles for a project visible to the current user.
     */
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getMembers(UUID projectId) {
        Project project = read(projectId);
        return project.getMembersWithRole().entrySet().stream()
                .map(e -> new ProjectMemberResponse(
                        e.getKey().getId(),
                        e.getKey().getUsername(),
                        e.getKey().getEmail(),
                        e.getValue()
                ))
                .toList();
    }

    /**
     * Adds a user to a project by user ID and role, requiring {@code MANAGER} authority.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @projectSecurity.isManager(#projectId)")
    public ProjectMemberResponse addMember(UUID projectId, UUID userId, ProjectRole role) {
        return addMember(projectId, userId, null, role);
    }

    /**
     * Adds a user to a project by either user ID or email address with the specified role,
     * and publishes a {@code MEMBER_ADDED} event.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @projectSecurity.isManager(#projectId)")
    public ProjectMemberResponse addMember(UUID projectId, UUID userId, String email, ProjectRole role) {
        Project project = read(projectId);
        User user;
        if (userId != null) {
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        } else if (email != null && !email.isBlank()) {
            user = userRepository.findByEmail(email.trim())
                    .orElseThrow(() -> new IllegalArgumentException("No account found for " + email.trim()));
        } else {
            throw new IllegalArgumentException("Either userId or email is required");
        }
        ProjectRole finalRole = role != null ? role : ProjectRole.MEMBER;
        project.addMember(user, finalRole);
        projectRepository.save(project);
        eventPublisher.publishEvent(new AuditEvent(
                "MEMBER_ADDED", "PROJECT", projectId, securityContext.getCurrentUserId(),
                Map.of("memberId", user.getId().toString(), "username", user.getUsername(), "role", finalRole.name())));
        return new ProjectMemberResponse(user.getId(), user.getUsername(), user.getEmail(), finalRole);
    }

    /**
     * Removes a member from a project and unassigns their tasks in that project, enforcing
     * the sole-manager invariant so a project never loses its last {@code MANAGER}.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @projectSecurity.isManager(#projectId)")
    public void removeMember(UUID projectId, UUID userId) {
        Project project = read(projectId);
        if (project.isManager(userId)) {
            long managerCount = project.getMembersWithRole().values().stream()
                    .filter(r -> r == ProjectRole.MANAGER)
                    .count();
            if (managerCount <= 1) {
                throw new ProjectConflictException("Cannot remove the only manager of a project");
            }
        }
        taskRepository.unassignUserFromProject(projectId, userId);
        project.removeMember(userId);
        projectRepository.save(project);
        eventPublisher.publishEvent(new AuditEvent(
                "MEMBER_REMOVED", "PROJECT", projectId, securityContext.getCurrentUserId(),
                Map.of("memberId", userId.toString())));
    }

    /**
     * Updates a project member's role while enforcing the sole-manager invariant,
     * and publishes a {@code MEMBER_ROLE_CHANGED} event.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @projectSecurity.isManager(#projectId)")
    public ProjectMemberResponse updateMemberRole(UUID projectId, UUID userId, ProjectRole role) {
        Project project = read(projectId);
        User user = project.getMembersWithRole().keySet().stream()
                .filter(u -> userId.equals(u.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this project: " + userId));
        if (role == ProjectRole.MEMBER && project.isManager(userId)) {
            long managerCount = project.getMembersWithRole().values().stream()
                    .filter(r -> r == ProjectRole.MANAGER)
                    .count();
            if (managerCount <= 1) {
                throw new ProjectConflictException("Cannot demote the only manager of a project");
            }
        }
        ProjectRole previousRole = project.getMembersWithRole().get(user);
        project.addMember(user, role);
        projectRepository.save(project);
        eventPublisher.publishEvent(new AuditEvent(
                "MEMBER_ROLE_CHANGED", "PROJECT", projectId, securityContext.getCurrentUserId(),
                Map.of(
                        "memberId", userId.toString(),
                        "username", user.getUsername(),
                        "role", role.name(),
                        "previousRole", previousRole != null ? previousRole.name() : ""
                )));
        return new ProjectMemberResponse(user.getId(), user.getUsername(), user.getEmail(), role);
    }

    /**
     * Builds a snapshot map of a project's key properties and manager IDs for audit and notification events.
     */
    private Map<String, Object> projectChanges(Project project) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("name", project.getName());
        if (project.getStatus() != null) {
            changes.put("status", project.getStatus().name());
        }
        if (project.getPriority() != null) {
            changes.put("priority", project.getPriority().name());
        }
        if (project.getMembersWithRole() != null) {
            List<String> managerIds = project.getMembersWithRole().entrySet().stream()
                    .filter(e -> e.getValue() == ProjectRole.MANAGER && e.getKey() != null && e.getKey().getId() != null)
                    .map(e -> e.getKey().getId().toString())
                    .toList();
            changes.put("managerIds", managerIds);
        }
        return changes;
    }
}
