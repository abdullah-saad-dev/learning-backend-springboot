package com.example.demo.tasks;

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

    @GetMapping
    public List<Task> findAll() {
        return service.findAll();
    }

    @GetMapping("/done")
    public List<Task> findDone() {
        return service.findByDone(true);
    }

    @GetMapping("/pending")
    public List<Task> findPending() {
        return service.findByDone(false);
    }

    @GetMapping("/{title}")
    public Task findByTitle(@PathVariable("title") String title) {
        return service.findByTitle(title);
    }

    @PostMapping
    public ResponseEntity<Task> create(@Valid @RequestBody TaskRequest r) {
        Task task = service.create(r.title(), r.details(), r.done());
        URI location = UriComponentsBuilder.fromPath("/api/tasks/{title}")
                .buildAndExpand(task.title())
                .encode()
                .toUri();
        return ResponseEntity.created(location).body(task);
    }

    @PutMapping("/{title}")
    public ResponseEntity<Task> update(@PathVariable("title") String title, @Valid @RequestBody TaskRequest r) {
        Task task = service.update(title, r.title(), r.details(), r.done());
        return ResponseEntity.ok(task);
    }

    @PatchMapping("/{title}")
    public ResponseEntity<Task> setDone(@PathVariable("title") String title, @Valid @RequestBody TaskPatch r) {
        Task task = service.setDone(title, r.done());
        return ResponseEntity.ok(task);
    }

    @DeleteMapping("/{title}")
    public ResponseEntity<Void> delete(@PathVariable("title") String title) {
        service.deleteByTitle(title);
        return ResponseEntity.noContent().build();
    }
}
