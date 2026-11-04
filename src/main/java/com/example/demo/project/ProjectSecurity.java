package com.example.demo.project;

import com.example.demo.auth.AppSecurityContext;
import com.example.demo.project.exceptions.ProjectNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("projectSecurity")
public class ProjectSecurity {
    private final ProjectRepository projectRepository;
    private final AppSecurityContext securityContext;

    public ProjectSecurity(ProjectRepository projectRepository, AppSecurityContext securityContext) {
        this.projectRepository = projectRepository;
        this.securityContext = securityContext;
    }

    /**
     * Checks whether the current user is a {@code MANAGER} of the specified project.
     * Throws {@link ProjectNotFoundException} (404) if the user is not a member of the project at all,
     * preventing cross-tenant project existence enumeration.
     */
    public boolean isManager(UUID projectId) {
        if (projectId == null) {
            throw new ProjectNotFoundException("no project with id null");
        }
        UUID currentUserId = securityContext.getCurrentUserId();
        if (currentUserId == null || !projectRepository.isMember(projectId, currentUserId)) {
            throw new ProjectNotFoundException("no project with id " + projectId);
        }
        return projectRepository.isManager(projectId, currentUserId);
    }

    /**
     * Checks whether the current user belongs to the specified project in any role.
     */
    public boolean isMember(UUID projectId) {
        if (projectId == null) {
            return false;
        }
        UUID currentUserId = securityContext.getCurrentUserId();
        if (currentUserId == null) {
            return false;
        }
        return projectRepository.isMember(projectId, currentUserId);
    }
}
