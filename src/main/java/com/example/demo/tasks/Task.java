package com.example.demo.tasks;

import java.time.Instant;

public record Task(String id, String title, String details, Instant createdAt, boolean done) {

}
