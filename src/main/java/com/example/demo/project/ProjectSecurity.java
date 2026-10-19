package com.example.demo.project;

import com.example.demo.auth.AppSecurityContext;
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

    public boolean isManager(UUID projectId) {
        if (projectId == null) {
            return false;
        }
        UUID currentUserId = securityContext.getCurrentUserId();
        if (currentUserId == null) {
            return false;
        }
        return projectRepository.isManager(projectId, currentUserId);
    }

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
