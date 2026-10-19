package com.example.demo.project;

import com.example.demo.auth.AppSecurityContext;
import com.example.demo.project.dtos.*;
import com.example.demo.project.enums.ProjectPriority;
import com.example.demo.project.enums.ProjectStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RequestMapping("api/projects")
@RestController
public class ProjectController {

    private final ProjectService projectService;
    private final AppSecurityContext securityContext;

    public ProjectController(ProjectService projectService, AppSecurityContext securityContext) {
        this.projectService = projectService;
        this.securityContext = securityContext;
    }

    @GetMapping
    public List<ProjectResponse> search(@RequestParam(name = "name", required = false) String name,
                                        @RequestParam(name = "priority", required = false) ProjectPriority priority,
                                        @RequestParam(name = "status", required = false) ProjectStatus status) {
        UUID currentUserId = securityContext.getCurrentUserId();
        return ProjectResponse.of(projectService.search(name, status, priority), currentUserId);
    }

    @GetMapping("/{projectId}")
    public ProjectResponse findById(@PathVariable("projectId") UUID projectId) {
        UUID currentUserId = securityContext.getCurrentUserId();
        return ProjectResponse.of(projectService.findById(projectId), currentUserId);
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest r) {
        UUID currentUserId = securityContext.getCurrentUserId();
        ProjectResponse project = ProjectResponse.of(projectService.create(r), currentUserId);
        return ResponseEntity
                .created(createUri(project.id()))
                .body(project);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @RequestParam(name = "version") Long expectedVersion) {
        projectService.delete(id, expectedVersion);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> update(@Valid @RequestBody UpdateProjectRequest r) {
        UUID currentUserId = securityContext.getCurrentUserId();
        ProjectResponse response = ProjectResponse.of(projectService.update(r), currentUserId);
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/{id}/members")
    public List<ProjectMemberResponse> getMembers(@PathVariable("id") UUID id) {
        return projectService.getMembers(id);
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ProjectMemberResponse> addMember(@PathVariable("id") UUID id,
                                                           @Valid @RequestBody AddProjectMemberRequest r) {
        ProjectMemberResponse member = projectService.addMember(id, r.userId(), r.role());
        return ResponseEntity.ok(member);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable("id") UUID id,
                                             @PathVariable("userId") UUID userId) {
        projectService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/members/{userId}/role")
    public ResponseEntity<ProjectMemberResponse> updateMemberRole(@PathVariable("id") UUID id,
                                                                  @PathVariable("userId") UUID userId,
                                                                  @Valid @RequestBody UpdateProjectMemberRoleRequest r) {
        ProjectMemberResponse member = projectService.updateMemberRole(id, userId, r.role());
        return ResponseEntity.ok(member);
    }

    private URI createUri(UUID id) {
        return UriComponentsBuilder.fromPath("/api/projects/{id}")
                .buildAndExpand(id)
                .encode()
                .toUri();
    }
}
