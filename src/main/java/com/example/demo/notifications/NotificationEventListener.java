package com.example.demo.notifications;

import com.example.demo.audit.AuditEvent;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.repository.UserRepository;
import com.example.demo.project.Project;
import com.example.demo.project.ProjectRepository;
import com.example.demo.project.enums.ProjectRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Asynchronous event listener that sends email notifications after a database transaction commits:
 * <ul>
 *   <li>When a user is assigned to a task ({@code TASK_CREATED} or {@code TASK_UPDATED} with a new assignee)</li>
 *   <li>When a user becomes a {@code MANAGER} of a project ({@code PROJECT_CREATED}, {@code MEMBER_ADDED}, or {@code MEMBER_ROLE_CHANGED})</li>
 * </ul>
 * Bound to {@link TransactionPhase#AFTER_COMMIT} and {@link Async} so slow SMTP I/O never blocks HTTP requests
 * and rolled-back transactions never trigger emails.
 */
@Slf4j
@Component
public class NotificationEventListener {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final JavaMailSender mailSender;
    private final String mailFrom;

    public NotificationEventListener(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            JavaMailSender mailSender,
            @Value("${app.mail.from:no-reply@managable.local}") String mailFrom
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
    }

    /**
     * Dispatches asynchronous email notifications after a task or project mutation commits.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuditEvent(AuditEvent event) {
        if (event == null || event.action() == null) {
            return;
        }
        Map<String, Object> changes = event.changes() != null ? event.changes() : Map.of();

        switch (event.action()) {
            case "TASK_CREATED" -> handleTaskAssigned(changes, false);
            case "TASK_UPDATED" -> handleTaskAssigned(changes, true);
            case "PROJECT_CREATED" -> handleProjectCreated(event, changes);
            case "MEMBER_ADDED" -> handleProjectManagerAdded(event, changes, false);
            case "MEMBER_ROLE_CHANGED" -> handleProjectManagerAdded(event, changes, true);
            default -> {
            }
        }
    }

    /**
     * Sends a task-assignment email when a task is created with an assignee or updated with a new assignee.
     */
    private void handleTaskAssigned(Map<String, Object> changes, boolean isUpdate) {
        String assigneeIdStr = asString(changes.get("assigneeId"));
        if (assigneeIdStr == null || assigneeIdStr.isBlank()) {
            return;
        }
        if (isUpdate) {
            String previousAssigneeIdStr = asString(changes.get("previousAssigneeId"));
            if (Objects.equals(assigneeIdStr, previousAssigneeIdStr)) {
                // Assignee did not change on this task update
                return;
            }
        }

        UUID assigneeId = parseUuid(assigneeIdStr);
        if (assigneeId == null) {
            return;
        }

        Optional<User> assigneeOpt = userRepository.findById(assigneeId);
        if (assigneeOpt.isEmpty()) {
            return;
        }

        User assignee = assigneeOpt.get();
        String taskTitle = Optional.ofNullable(asString(changes.get("title"))).orElse("Untitled task");
        String dueDate = asString(changes.get("dueDate"));
        String projectName = resolveProjectName(parseUuid(asString(changes.get("projectId"))));

        String subject = "Managable: You have been assigned a task — " + taskTitle;
        StringBuilder body = new StringBuilder();
        body.append("Hello ").append(assignee.getUsername()).append(",\n\n")
            .append("You have been assigned a task in Managable:\n\n")
            .append("  • Task: ").append(taskTitle).append("\n");
        if (projectName != null) {
            body.append("  • Project: ").append(projectName).append("\n");
        }
        if (dueDate != null && !dueDate.isBlank()) {
            body.append("  • Due date: ").append(dueDate).append("\n");
        }
        body.append("\nOpen Managable to view the details and track your progress.\n");

        sendEmail(assignee.getEmail(), subject, body.toString(), "task_assigned");
    }

    /**
     * Notifies all initial project managers when a new project is created.
     */
    private void handleProjectCreated(AuditEvent event, Map<String, Object> changes) {
        String projectName = Optional.ofNullable(asString(changes.get("name")))
                .orElseGet(() -> resolveProjectName(event.entityId()));
        String resolvedName = projectName != null ? projectName : "New project";

        Object rawManagerIds = changes.get("managerIds");
        if (rawManagerIds instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                UUID managerId = parseUuid(asString(item));
                if (managerId != null) {
                    userRepository.findById(managerId)
                            .ifPresent(manager -> sendProjectManagerEmail(manager, resolvedName));
                }
            }
            return;
        }

        if (event.actorId() != null) {
            userRepository.findById(event.actorId())
                    .ifPresent(manager -> sendProjectManagerEmail(manager, resolvedName));
        }
    }

    /**
     * Sends a notification email when a user is added to a project as {@code MANAGER}
     * or promoted from {@code MEMBER} to {@code MANAGER}.
     */
    private void handleProjectManagerAdded(AuditEvent event, Map<String, Object> changes, boolean isRoleChange) {
        String role = asString(changes.get("role"));
        if (!ProjectRole.MANAGER.name().equals(role)) {
            return;
        }
        if (isRoleChange) {
            String previousRole = asString(changes.get("previousRole"));
            if (ProjectRole.MANAGER.name().equals(previousRole)) {
                return;
            }
        }

        UUID memberId = parseUuid(asString(changes.get("memberId")));
        if (memberId == null) {
            return;
        }

        Optional<User> userOpt = userRepository.findById(memberId);
        if (userOpt.isEmpty()) {
            return;
        }

        String projectName = Optional.ofNullable(resolveProjectName(event.entityId())).orElse("your project");
        sendProjectManagerEmail(userOpt.get(), projectName);
    }

    /**
     * Formats and dispatches the project-manager assignment email for a given user and project.
     */
    private void sendProjectManagerEmail(User manager, String projectName) {
        String subject = "Managable: You are now a manager of " + projectName;
        String body = """
                Hello %s,

                You have been assigned as a MANAGER for the project "%s" in Managable.

                As a project manager, you can plan tasks, assign work to members, manage roles, and update project settings.
                """.formatted(manager.getUsername(), projectName);

        sendEmail(manager.getEmail(), subject, body, "project_manager_assigned");
    }

    /**
     * Sends a plain-text email via {@link JavaMailSender}, logging any SMTP transport failure
     * as a warning so background notification errors never disrupt application flow.
     */
    private void sendEmail(String recipientEmail, String subject, String body, String notificationType) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(recipientEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.atInfo()
                    .setMessage("notification email sent")
                    .addKeyValue("type", notificationType)
                    .addKeyValue("email", recipientEmail)
                    .log();
        } catch (Exception ex) {
            log.atWarn()
                    .setMessage("failed to send notification email")
                    .addKeyValue("type", notificationType)
                    .addKeyValue("email", recipientEmail)
                    .setCause(ex)
                    .log();
        }
    }

    /**
     * Looks up the display name of a project by its identifier, returning {@code null} if not found.
     */
    private String resolveProjectName(UUID projectId) {
        if (projectId == null) {
            return null;
        }
        return projectRepository.findById(projectId)
                .map(Project::getName)
                .orElse(null);
    }

    /**
     * Converts a nullable payload value into its string representation.
     */
    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    /**
     * Safely parses a UUID string, returning {@code null} if blank or malformed.
     */
    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
