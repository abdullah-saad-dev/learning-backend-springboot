package com.example.demo.project;

import com.example.demo.project.dtos.CreateProjectRequest;
import com.example.demo.project.dtos.ProjectResponse;
import com.example.demo.project.enums.ProjectPriority;
import com.example.demo.project.enums.ProjectStatus;
import jakarta.validation.Valid;
import org.springframework.http.RequestEntity;
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


    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> search(@RequestParam(name = "name", required = false) String name,
                                        @RequestParam(name = "priority", required = false) ProjectPriority priority,
                                        @RequestParam(name = "status", required = false)ProjectStatus status) {
        return ProjectResponse.of(projectService.search(name, status, priority));
    }

    @GetMapping("/{projectId}")
    public ProjectResponse findById(@PathVariable("projectId") UUID projectId) {
        return ProjectResponse.of(projectService.findById(projectId));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest r) {
        ProjectResponse project = ProjectResponse.of(projectService.create(r));
        return ResponseEntity
                .created(createUri(project.id()))
                .body(project);
    }

    private URI createUri(UUID id) {
        return UriComponentsBuilder.fromPath("/api/tasks/{id}")
                .buildAndExpand(id)
                .encode()
                .toUri();
    }
}
