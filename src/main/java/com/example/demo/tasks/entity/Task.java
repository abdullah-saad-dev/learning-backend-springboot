package com.example.demo.tasks.entity;

import com.example.demo.auth.entity.User;
import com.example.demo.project.Project;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id")
    private UUID id;

    @Column(name = "title")
    private String title;

    @Column(name = "details")
    private String details;

    @Column(name = "created_at")
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name ="project_id", nullable = false)
    private Project project;

    @Column(name = "done")
    private boolean done;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToMany
    @JoinTable(
            name = "tasks_users",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> assignees;
}
