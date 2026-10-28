package com.example.demo.audit;

import java.util.Map;
import java.util.UUID;

public record AuditEvent(
        String action,
        String entityType,
        UUID entityId,
        UUID actorId,
        Map<String, Object> changes
) {
}
