package com.example.demo.project;

import com.example.demo.auth.AppSecurityContext;
import com.example.demo.auth.repository.UserRepository;
import com.example.demo.project.dtos.CreateProjectRequest;
import com.example.demo.project.enums.ProjectPriority;
import com.example.demo.project.enums.ProjectStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
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
    public List<Project> search(String name, ProjectStatus status, ProjectPriority priority) {

        List<Project> readProjects = projectRepository.search(name, status, priority);
        log.atDebug()
                .addKeyValue("projectsIds", readProjects.stream().map(Project::getId).toList())
                .setMessage("projects read")
                .log();
        return readProjects;
    }
    public Project read(UUID id){
        Project readProject = projectRepository.findById(id).orElseThrow(() -> new ProjectNotFoundException("no project with id " + id));
        log.atDebug()
                .addKeyValue("projectId", readProject.getId())
                .setMessage("project read")
                .log();
        return readProject;
    }
    @Transactional
    public Project create(CreateProjectRequest r){
        Project project = Project.builder()
                .name(r.name())
                .createdAt(clock.instant())
                .description(r.description())
                .dueDate(r.dueDate())
                .startDate(r.startDate())
                .priority(r.priority())
                .status(r.status())
                .leader(userRepository.getReferenceById(securityContext.getCurrentUserId()))
                .members(r.members())
                .build();
        Project savedProject = projectRepository.save(project);
        log.atInfo()
                .addKeyValue("projectId", savedProject.getId())
                .addKeyValue("leaderId", savedProject.getLeader().getId())
                .setMessage("project created")
                .log();
        return savedProject;
    }
}
