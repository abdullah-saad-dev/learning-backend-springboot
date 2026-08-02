package com.example.demo.tasks;

import com.example.demo.tasks.exceptions.TaskNotFoundException;
import com.example.demo.tasks.repository.TaskRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskRepository tasks;

    public TaskController(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    public List<Task> findAll() {
        return tasks.findAll();
    }

    @GetMapping("/done")
    public List<Task> findDone() {
        return tasks.findByDone(true);
    }

    @GetMapping("/pending")
    public List<Task> findPending() {
        return tasks.findByDone(false);
    }

    @GetMapping("/{title}")
    public List<Task> findByTitle(@PathVariable("title") String title) {
        return tasks.findByTitle(title);
    }

    @PostMapping
    public ResponseEntity<Task> create(@Valid @RequestBody TaskRequest r) {
        // id 0 is a placeholder; save() assigns the real one and hands the task back.
        Task task = tasks.save(new Task(0, r.title(), r.details(), Instant.now(), r.done() != null && r.done()));
        return ResponseEntity.created(addLocation(task.title())).body(task);
    }

    private URI addLocation(String title) {
        return UriComponentsBuilder.fromPath("/api/tasks/{title}")
                .buildAndExpand(title)
                .encode()
                .toUri();
    }

    @PutMapping("/{title}")
    public ResponseEntity<Task> update(@PathVariable("title") String title, @Valid @RequestBody TaskRequest r) {
        Task current = first(title);
        Task updated = new Task(current.id(), r.title(), r.details(), current.createdAt(), r.done() != null && r.done());
        tasks.update(updated);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{title}")
    public ResponseEntity<Task> setDone(@PathVariable("title") String title, @Valid @RequestBody TaskPatch r) {
        Task current = first(title);
        Task updated = new Task(current.id(), current.title(), current.details(), current.createdAt(), r.done());
        tasks.update(updated);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{title}")
    public ResponseEntity<Void> delete(@PathVariable("title") String title) {
        tasks.delete(first(title));
        return ResponseEntity.noContent().build();
    }

    // Titles are no longer unique, so a title can name several tasks; the single-task
    // endpoints act on the first match.
    private Task first(String title) {
        return tasks.findByTitle(title).stream()
                .findFirst()
                .orElseThrow(() -> new TaskNotFoundException(title));
    }
}
