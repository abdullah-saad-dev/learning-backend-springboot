package com.example.demo.tasks.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
// toBuilder gives "copy this task with one field changed", which is how every edit and every
// defensive copy in the layers above is expressed.
@Builder(toBuilder = true)
// Deliberately not @Data: that would derive equals/hashCode from every mutable field, so a
// task's hash would change the moment Hibernate assigned its id on persist - silently losing
// it from any HashSet holding it. Identity is the id and nothing else (see below).
@Getter
@Setter
@ToString
@Entity
@Table(name = "tasks")
public class Task {
    // Boxed so an unsaved task has a null id rather than 0, which is indistinguishable from a
    // real id and makes "has this been persisted yet?" unanswerable.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "title")
    private String title;

    @Column(name = "details")
    private String details;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "done")
    private boolean done;


    @Version
    @Column(name = "version")
    private Long version;
}
