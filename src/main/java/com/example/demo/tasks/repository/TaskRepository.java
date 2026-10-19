package com.example.demo.tasks.repository;

import com.example.demo.tasks.entity.Task;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    // hibernate infers the type directly from the column but not from functions output.
    // The entity graph pulls assignees in the same query: open-in-view is off, so the
    // controller maps TaskResponse after the session is closed and a lazy collection
    // touched there would throw.
    @EntityGraph(attributePaths = "assignees")
    @Query("""
    select t from Task as t join t.project.membersWithRole as pu
        where key(pu).id = :userId
        and (cast(:title as String) is null or upper(t.title) = upper(cast(:title as String)))
        and (:done is null or t.done = :done)           
        order by t.createdAt desc       
    """)
    List<Task> search(@Param("userId") UUID userId, @Param("title") String title, @Param("done") Boolean done);

    // Backed by idx_tasks_users_user_id (V12): the lookup starts from the user, which the
    // (task_id, user_id) primary key cannot serve.
    @EntityGraph(attributePaths = "assignees")
    @Query("""
    select t from Task t join t.assignees a
        where a.id = :userId and t.project.id = :projectId
        order by t.createdAt desc
    """)
    List<Task> findAssignedToUserInProject(@Param("userId") UUID userId, @Param("projectId") UUID projectId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Task t WHERE t.id = :id AND t.version = :version")
    int deleteById(UUID id, long version);

    @Query("""
        select case when count(t) > 0 then true else false end
        from Task t join t.assignees a
        where t.id = :taskId and a.id = :userId
    """)
    boolean isUserAssignedToTask(@Param("taskId") UUID taskId, @Param("userId") UUID userId);
    @EntityGraph(attributePaths = "assignees")
    @Query("""
    SELECT t FROM Task t 
    JOIN t.project p
    JOIN p.membersWithRole m       
    WHERE t.id = :taskId   
        AND KEY(m).id = :userId 
    """)
    Optional<Task> findByIdAndUserId(@Param("taskId") UUID taskId, @Param("userId") UUID userId);

    @Query("""
        select case when count(t) > 0 then true else false end
        from Task t
        join t.project p
        join p.membersWithRole m
        where t.id = :taskId
          and key(m).id = :userId
          and value(m) = com.example.demo.project.enums.ProjectRole.MANAGER
    """)
    boolean isProjectManagerForTask(@Param("taskId") UUID taskId, @Param("userId") UUID userId);

}
