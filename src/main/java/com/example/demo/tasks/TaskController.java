package com.example.demo.tasks;

import com.example.demo.tasks.dtos.CreateTaskRequest;
import com.example.demo.tasks.dtos.SetDoneRequest;
import com.example.demo.tasks.dtos.TaskResponse;
import com.example.demo.tasks.dtos.UpdateTaskRequest;
import com.example.demo.tasks.entity.Task;
import com.example.demo.tasks.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaskResponse> findAll(@RequestParam(name = "title", required = false) String title,
                                      @RequestParam(name = "done", required = false) Boolean done,
                                      @AuthenticationPrincipal Jwt jwt) {
        return TaskResponse.of(service.search(getOwnerId(jwt), title, done));
    }
    // authentication.getName() returns the principle claim name which was set to sub in JwtConfig class
    @GetMapping("/{id}")
    public TaskResponse findById(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt jwt) {
        return TaskResponse.of(service.findByIdAndOwnerId(id, getOwnerId(jwt)));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest r,
                                               @AuthenticationPrincipal Jwt jwt) {
        TaskResponse task = TaskResponse.of(service.create(getOwnerId(jwt), r.title(), r.details(), r.done()));
        return ResponseEntity.created(createUri(task.id())).body(task);
    }

    private URI createUri(UUID id) {
        return UriComponentsBuilder.fromPath("/api/tasks/{id}")
                .buildAndExpand(id)
                .encode()
                .toUri();
    }

    @PatchMapping("/{id}/done")
    public ResponseEntity<TaskResponse> setDone(@PathVariable("id") UUID id,
                                                @Valid @RequestBody SetDoneRequest r,
                                                @AuthenticationPrincipal Jwt jwt) {

        Task patchedTask = service.setDone(id, getOwnerId(jwt), r.done(), r.version());
        TaskResponse taskResponse = TaskResponse.of(patchedTask);
        return ResponseEntity.ok(taskResponse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id,
                                       @RequestParam("version") long version,
                                       @AuthenticationPrincipal Jwt jwt) {
        service.delete(id, getOwnerId(jwt),version);
        return ResponseEntity.noContent().build();
    }

    @PutMapping({"/{id}"})
    public ResponseEntity<TaskResponse> updateTask(@PathVariable("id") UUID id,
                                                   @Valid @RequestBody UpdateTaskRequest r,
                                                   @AuthenticationPrincipal Jwt jwt) {
        Task updatedRequest = service.update(id, getOwnerId(jwt), r);
        TaskResponse taskResponse = TaskResponse.of(updatedRequest);
        return ResponseEntity.ok(taskResponse);
    }
    private UUID getOwnerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
