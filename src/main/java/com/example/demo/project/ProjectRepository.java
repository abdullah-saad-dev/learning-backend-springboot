package com.example.demo.project;

import com.example.demo.project.enums.ProjectPriority;
import com.example.demo.project.enums.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    @Query("""
                select p from Project as p 
                left join p.membersWithRole m     
                where (cast(:name as String) is null or upper(p.name) = upper(cast(:name as String)))
                and   (:status is null or :status = p.status)
                and (:priority is null or :priority = p.priority)
                and :userId = KEY(m).id             
            """)
    List<Project> search(@Param("name") String name, @Param("userId") UUID userId,
                         @Param("status") ProjectStatus status, @Param("priority") ProjectPriority priority);

    @Query("""
                SELECT p FROM Project as p
                LEFT JOIN p.membersWithRole m
                WHERE p.id = :projectId
                AND   KEY(m).id = :userId                    
            """)
    Optional<Project> findByIdAndUserId(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    @Query("""
        select case when count(p) > 0 then true else false end
        from Project p
        join p.membersWithRole m
        where p.id = :projectId
          and key(m).id = :userId
          and value(m) = com.example.demo.project.enums.ProjectRole.MANAGER
    """)
    boolean isManager(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    @Query("""
        select case when count(p) > 0 then true else false end
        from Project p
        join p.membersWithRole m
        where p.id = :projectId
          and key(m).id = :userId
    """)
    boolean isMember(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Project p WHERE p.id = :projectId AND p.version = :expectedVersion")
    int delete(@Param("projectId") UUID projectId, @Param("expectedVersion") long expectedVersion);
}
