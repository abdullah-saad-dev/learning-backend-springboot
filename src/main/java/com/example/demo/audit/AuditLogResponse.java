package com.example.demo.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String action,
        String entityType,
        UUID entityId,
        UUID actorId,
        Map<String, Object> changes,
        Instant createdAt
) {
    public static AuditLogResponse of(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getActorId(),
                log.getChanges(),
                log.getCreatedAt()
        );
    }

    public static List<AuditLogResponse> of(List<AuditLog> logs) {
        return logs.stream().map(AuditLogResponse::of).toList();
    }
}
