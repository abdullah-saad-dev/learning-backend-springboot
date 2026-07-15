package com.example.demo.notes;

public class NoteNotFoundException extends RuntimeException {
    public NoteNotFoundException(String id) {
        super("Note with id " + id + " not found");
    }
}
