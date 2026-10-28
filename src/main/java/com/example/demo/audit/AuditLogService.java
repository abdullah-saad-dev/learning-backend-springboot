package com.example.demo.audit;

import com.example.demo.auth.AppSecurityContext;
import com.example.demo.project.ProjectRepository;
import com.example.demo.project.exceptions.ProjectNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ProjectRepository projectRepository;
    private final AppSecurityContext securityContext;
    private final Clock clock;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           ProjectRepository projectRepository,
                           AppSecurityContext securityContext,
                           Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.projectRepository = projectRepository;
        this.securityContext = securityContext;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void onAuditEvent(AuditEvent event) {
        if (event.actorId() == null || event.entityId() == null) {
            return;
        }
        AuditLog entry = AuditLog.builder()
                .action(event.action())
                .entityType(event.entityType())
                .entityId(event.entityId())
                .actorId(event.actorId())
                .changes(event.changes() != null ? event.changes() : Map.of())
                .createdAt(clock.instant())
                .build();
        auditLogRepository.save(entry);
        log.atDebug()
                .setMessage("audit log recorded")
                .addKeyValue("action", event.action())
                .addKeyValue("entityType", event.entityType())
                .addKeyValue("entityId", event.entityId())
                .addKeyValue("actorId", event.actorId())
                .log();
    }

    public List<AuditLogResponse> getProjectActivity(UUID projectId) {
        UUID userId = securityContext.getCurrentUserId();
        if (!projectRepository.isMember(projectId, userId)) {
            throw new ProjectNotFoundException("no project with id " + projectId);
        }
        return AuditLogResponse.of(auditLogRepository.findProjectActivity(projectId));
    }
}
