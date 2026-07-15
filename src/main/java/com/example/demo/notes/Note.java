package com.example.demo.notes;

import java.time.Instant;

public record Note(String id, String title, Instant createdAt) {

}
