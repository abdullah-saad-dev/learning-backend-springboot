package com.example.demo.tasks.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
// toBuilder gives "copy this task with one field changed", which is how every edit and every
// defensive copy in the layers above is expressed.
@Builder(toBuilder = true)
@Data
@Entity
@Table(name = "tasks")
public class TaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "title")
    private String title;

    @Column(name = "details")
    private String details;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "done")
    private boolean done;
}
