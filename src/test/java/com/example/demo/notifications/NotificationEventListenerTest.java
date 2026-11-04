package com.example.demo.notifications;

import com.example.demo.PostgresTestContainer;
import com.example.demo.audit.AuditEvent;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.enums.Role;
import com.example.demo.auth.repository.UserRepository;
import com.example.demo.project.Project;
import com.example.demo.project.ProjectRepository;
import com.example.demo.project.enums.ProjectPriority;
import com.example.demo.project.enums.ProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestContainer.class)
class NotificationEventListenerTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<SimpleMailMessage> sentMessages = new ArrayList<>();
    private NotificationEventListener listener;
    private User alice;
    private Project project;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate table projects, users cascade");
        sentMessages.clear();

        JavaMailSender fakeSender = new JavaMailSenderImpl() {
            @Override
            public void send(SimpleMailMessage simpleMessage) {
                sentMessages.add(simpleMessage);
            }
        };

        listener = new NotificationEventListener(
                userRepository,
                projectRepository,
                fakeSender,
                "no-reply@managable.app"
        );

        alice = userRepository.save(User.builder()
                .username("alice")
                .email("alice@example.com")
                .password("{noop}secret")
                .role(Role.USER)
                .enabled(true)
                .createdAt(Instant.now())
                .build());

        project = projectRepository.save(Project.builder()
                .name("Platform Rewrite")
                .description("Core platform migration")
                .status(ProjectStatus.IN_PROGRESS)
                .priority(ProjectPriority.HIGH)
                .startDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(14))
                .createdAt(Instant.now())
                .build());
    }

    @Test
    void taskAssignedEventSendsEmailToAssignee() {
        listener.onAuditEvent(new AuditEvent(
                "TASK_CREATED",
                "TASK",
                UUID.randomUUID(),
                alice.getId(),
                Map.of(
                        "projectId", project.getId().toString(),
                        "title", "Implement async notifications",
                        "assigneeId", alice.getId().toString(),
                        "dueDate", "2026-11-15"
                )
        ));

        assertThat(sentMessages).hasSize(1);
        SimpleMailMessage msg = sentMessages.getFirst();
        assertThat(msg.getFrom()).isEqualTo("no-reply@managable.app");
        assertThat(msg.getTo()).containsExactly("alice@example.com");
        assertThat(msg.getSubject()).contains("Implement async notifications");
        assertThat(msg.getText())
                .contains("Platform Rewrite")
                .contains("2026-11-15");
    }

    @Test
    void taskUpdateWithUnchangedAssigneeDoesNotSendDuplicateEmail() {
        listener.onAuditEvent(new AuditEvent(
                "TASK_UPDATED",
                "TASK",
                UUID.randomUUID(),
                alice.getId(),
                Map.of(
                        "projectId", project.getId().toString(),
                        "title", "Edited title only",
                        "assigneeId", alice.getId().toString(),
                        "previousAssigneeId", alice.getId().toString()
                )
        ));

        assertThat(sentMessages).isEmpty();
    }

    @Test
    void projectManagerAddedOrPromotedSendsEmailWhileMemberDoesNot() {
        // Adding as regular MEMBER should not send a manager notification
        listener.onAuditEvent(new AuditEvent(
                "MEMBER_ADDED",
                "PROJECT",
                project.getId(),
                alice.getId(),
                Map.of(
                        "memberId", alice.getId().toString(),
                        "username", alice.getUsername(),
                        "role", "MEMBER"
                )
        ));
        assertThat(sentMessages).isEmpty();

        // Adding as MANAGER sends notification
        listener.onAuditEvent(new AuditEvent(
                "MEMBER_ADDED",
                "PROJECT",
                project.getId(),
                alice.getId(),
                Map.of(
                        "memberId", alice.getId().toString(),
                        "username", alice.getUsername(),
                        "role", "MANAGER"
                )
        ));
        assertThat(sentMessages).hasSize(1);
        assertThat(sentMessages.getFirst().getTo()).containsExactly("alice@example.com");
        assertThat(sentMessages.getFirst().getSubject()).contains("Platform Rewrite");
        assertThat(sentMessages.getFirst().getText()).contains("MANAGER");
    }
}
