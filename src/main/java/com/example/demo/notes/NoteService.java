package com.example.demo.notes;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NoteService {
    private final Map<String, Note> notes = new ConcurrentHashMap<>();

    public List<Note> findAll() {
        return List.copyOf(notes.values());
    }

    public Note findById(String id) {
        Note note = notes.get(id);
        if (note != null)
            return note;
        else
            throw new NoteNotFoundException(id);
    }

    public Note create(String title) {
        String id = UUID.randomUUID().toString();
        Note note = new Note(id, title, Instant.now());
        notes.put(id, note);
        return note;
    }

    public Note delete(String id) {
        Note note = notes.remove(id);
        if (note == null)
            throw new NoteNotFoundException(id);
        return note;
    }

    public NoteUpdated replace(String id, String newTitle) {
        boolean[] replaced = {false};
        Note note = notes.compute(id, (key, current) -> {
            if(current == null)
                return new Note(id, newTitle, Instant.now());
            else {
                replaced[0] = true;
                return new Note(id, newTitle, current.createdAt());
            }
        });
        return new NoteUpdated(note, replaced[0]);
    }

}
