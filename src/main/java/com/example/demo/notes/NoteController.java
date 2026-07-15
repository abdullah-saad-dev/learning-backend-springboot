package com.example.demo.notes;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NoteController {
    private final NoteService service;

    public NoteController(NoteService service) {
        this.service = service;
    }

    @GetMapping
    public List<Note> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Note findById(@PathVariable("id") String id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<Note> create(@Valid @RequestBody NoteRequest r) {
        Note note = service.create(r.title());
        URI location = URI.create("/api/notes/" + note.id());
        return ResponseEntity.created(location).body(note);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/{id}")
    public ResponseEntity<Note> replace(@PathVariable("id") String id, @Valid @RequestBody NoteRequest r){
        NoteUpdated note = service.replace(id,r.title());
        boolean created = !note.isUpdated();
        if(created){
            URI location = URI.create("/api/notes/" + note.note().id());
            return ResponseEntity.created(location).body(note.note());
        }
        else
            return ResponseEntity.ok(note.note());

    }
}
