package com.example.demo.project;

import com.example.demo.auth.entity.User;
import com.example.demo.project.enums.ProjectPriority;
import com.example.demo.project.enums.ProjectRole;
import com.example.demo.project.enums.ProjectStatus;
import com.example.demo.tasks.entity.Task;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Setter
@Getter
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column
    private String name;

    @Column
    private String description;

    @Column
    @Enumerated(EnumType.STRING)
    private ProjectStatus status;

    @Column
    @Enumerated(EnumType.STRING)
    private ProjectPriority priority;


    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "projects_users",
            joinColumns = @JoinColumn(name = "project_id")
    )
    @MapKeyJoinColumn(name = "user_id")
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Map<User, ProjectRole> membersWithRole = new HashMap<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Version
    @Column
    private long version;

    public ProjectRole getRoleForUser(UUID userId) {
        if (membersWithRole == null || userId == null) {
            return null;
        }
        return membersWithRole.entrySet().stream()
                .filter(e -> userId.equals(e.getKey().getId()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    public boolean isManager(UUID userId) {
        return ProjectRole.MANAGER.equals(getRoleForUser(userId));
    }

    public boolean isMember(UUID userId) {
        return getRoleForUser(userId) != null;
    }

    public int getMemberCount() {
        return membersWithRole != null ? membersWithRole.size() : 0;
    }

    public void addMember(User user, ProjectRole role) {
        if (membersWithRole == null) {
            membersWithRole = new HashMap<>();
        }
        membersWithRole.put(user, role != null ? role : ProjectRole.MEMBER);
    }

    public void removeMember(UUID userId) {
        if (membersWithRole == null || userId == null) {
            return;
        }
        membersWithRole.entrySet().removeIf(e -> userId.equals(e.getKey().getId()));
    }
}
