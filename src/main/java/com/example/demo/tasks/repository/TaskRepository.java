package com.example.demo.tasks.repository;

import com.example.demo.tasks.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    @Query("""
    select t from Task as t
        where (cast(:title as String) is null or upper(t.title) = upper(cast(:title as String)))
        and (:done is null or t.done = :done)   
        and :ownerId = t.ownerId        
        order by t.createdAt desc       
    """)
    List<Task> search(@Param("title") String title, @Param("ownerId") UUID ownerId, @Param("done") Boolean done);

    Optional<Task> findByIdAndOwnerId(UUID taskId, UUID ownerId);

}
