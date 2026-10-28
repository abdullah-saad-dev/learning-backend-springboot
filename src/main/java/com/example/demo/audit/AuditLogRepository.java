package com.example.demo.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);

    @Query(value = """
            SELECT * FROM audit_logs a
            WHERE (a.entity_type = 'PROJECT' AND a.entity_id = :projectId)
               OR (a.entity_type = 'TASK' AND a.changes ->> 'projectId' = CAST(:projectId AS text))
               OR (a.entity_type = 'TASK' AND a.entity_id IN (SELECT t.id FROM tasks t WHERE t.project_id = :projectId))
            ORDER BY a.created_at DESC
            """, nativeQuery = true)
    List<AuditLog> findProjectActivity(@Param("projectId") UUID projectId);
}
