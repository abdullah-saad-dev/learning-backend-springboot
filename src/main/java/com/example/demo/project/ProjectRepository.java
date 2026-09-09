package com.example.demo.project;

import com.example.demo.project.enums.ProjectPriority;
import com.example.demo.project.enums.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    @Query("""
                select p from Project as p
                where (cast(:name as String) is null or upper(p.name) = upper(cast(:name as String)))
                and   (:status is null or :status = p.status)
                and (:priority is null or :priority = p.priority)
            """)
    List<Project> search(String name, ProjectStatus status, ProjectPriority priority);
}
