package com.example.demo.project;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final Clock clock;
    private final AppSecurityContext securityContext;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository, Clock clock,
                          AppSecurityContext securityContext, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.clock = clock;
        this.securityContext = securityContext;
        this.userRepository = userRepository;
    }

    public Project findById(UUID id) {
        return read(id);
    }

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

    @Transactional(readOnly = true)
    public Project read(UUID id) {
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
        Project project = Project.builder()
                .name(r.name())
                .createdAt(clock.instant())
                .description(r.description())
                .dueDate(r.dueDate())
                .startDate(r.startDate())
                .priority(r.priority())
                .status(r.status())
                .membersWithRole(members)
                .build();
        Project savedProject = projectRepository.save(project);
        log.atInfo()
                .addKeyValue("projectId", savedProject.getId())
                .addKeyValue("managerId", current.getId())
                .setMessage("project created")
                .log();
        return savedProject;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @projectSecurity.isManager(#id)")
    public void delete(UUID id, long expectedVersion) {
        int deletedRows = projectRepository.delete(id, expectedVersion);
        UUID userId = securityContext.getCurrentUserId();
        if (deletedRows == 0) {
            throw new ProjectConflictException("the project changed while viewing, please refresh the page and try again");
        }
        log.atInfo()
                .addKeyValue("projectId", id)
                .setMessage("project deleted")
                .addKeyValue("userId", userId)
                .log();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @projectSecurity.isManager(#r.id())")
    public Project update(UpdateProjectRequest r) {
        UUID userId = securityContext.getCurrentUserId();
        Project project = read(r.id());
        if (r.name() != null) project.setName(r.name());
        if (r.description() != null) project.setDescription(r.description());
        if (r.dueDate() != null) project.setDueDate(r.dueDate());
        if (r.startDate() != null) project.setStartDate(r.startDate());
        if (r.priority() != null) project.setPriority(r.priority());
        if (r.status() != null) project.setStatus(r.status());

        Project saved = projectRepository.save(project);
        log.atInfo()
                .addKeyValue("projectId", r.id())
                .setMessage("project updated")
                .addKeyValue("userId", userId)
                .log();
        return saved;
    }

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

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @projectSecurity.isManager(#projectId)")
    public ProjectMemberResponse addMember(UUID projectId, UUID userId, ProjectRole role) {
        Project project = read(projectId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        ProjectRole finalRole = role != null ? role : ProjectRole.MEMBER;
        project.addMember(user, finalRole);
        projectRepository.save(project);
        return new ProjectMemberResponse(user.getId(), user.getUsername(), user.getEmail(), finalRole);
    }

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
        project.removeMember(userId);
        projectRepository.save(project);
    }

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
        project.addMember(user, role);
        projectRepository.save(project);
        return new ProjectMemberResponse(user.getId(), user.getUsername(), user.getEmail(), role);
    }
}
