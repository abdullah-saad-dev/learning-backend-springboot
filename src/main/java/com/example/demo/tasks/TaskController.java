package com.example.demo.tasks;

import com.example.demo.tasks.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    // Titles are not unique, so searching by one is a filter on the collection rather than a
    // path segment: /api/tasks/{id} always addresses exactly one task.
    @GetMapping
    public List<TaskResponse> find(@RequestParam(name = "title", required = false) String title) {
        return TaskResponse.of(title == null ? service.findAll() : service.findByTitle(title));
    }

    @GetMapping("/done")
    public List<TaskResponse> findDone() {
        return TaskResponse.of(service.findByDone(true));
    }

    @GetMapping("/pending")
    public List<TaskResponse> findPending() {
        return TaskResponse.of(service.findByDone(false));
    }

    @GetMapping("/{id}")
    public TaskResponse findById(@PathVariable("id") int id) {
        return TaskResponse.of(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskRequest r) {
        TaskResponse task = TaskResponse.of(service.create(r.title(), r.details(), r.done()));
        return ResponseEntity.created(createUri(task.id())).body(task);
    }

    private URI createUri(int id) {
        return UriComponentsBuilder.fromPath("/api/tasks/{id}")
                .buildAndExpand(id)
                .encode()
                .toUri();
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> update(@PathVariable("id") int id, @Valid @RequestBody TaskRequest r) {
        return ResponseEntity.ok(TaskResponse.of(service.update(id, r.title(), r.details(), r.done())));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TaskResponse> setDone(@PathVariable("id") int id, @Valid @RequestBody TaskPatch r) {
        return ResponseEntity.ok(TaskResponse.of(service.setDone(id, r.done())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") int id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
