package com.example.demo.tasks.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;

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
public class TaskEntity {
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

    // Bumped by the store on every write. A writer states the version it read, and the write is
    // rejected if the row has moved on since - that is what turns last-writer-wins into a
    // detected conflict. Boxed so an unsaved task has no version rather than a fake 0.
    @Version
    @Column(name = "version")
    private Long version;

    // Two entities are the same task when they carry the same id; an unsaved task (null id) is
    // only ever equal to itself.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskEntity other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    // Constant by design: the id is null before persist and set afterwards, so hashing it would
    // move the entity between buckets mid-life. A constant costs lookups in a HashSet full of
    // tasks and buys correctness everywhere else.
    @Override
    public int hashCode() {
        return TaskEntity.class.hashCode();
    }
}
