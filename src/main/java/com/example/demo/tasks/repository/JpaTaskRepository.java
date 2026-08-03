package com.example.demo.tasks.repository;

import com.example.demo.tasks.entity.TaskEntity;
import com.example.demo.tasks.exceptions.TaskNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * The production store. Selected by profile rather than @Primary so exactly one TaskRepository
 * bean exists per environment instead of two beans and a tiebreak.
 * <p>
 * Nothing here opens a transaction: the unit of work is a service call, not a store call, so
 * TaskService owns the boundary. That is what lets a read and the write that depends on it
 * share one transaction - see the note on TaskService.read.
 */
@Repository
@Profile("!inmemory")
public class JpaTaskRepository implements TaskRepository {
    private final EntityManager em;

    public JpaTaskRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public TaskEntity save(TaskEntity task) {
        em.persist(task);
        return task;
    }

    @Override
    public TaskEntity delete(TaskEntity task) {
        // Safe against a detached argument: findById ran inside this same transaction, so the
        // instance handed to us is still managed.
        em.remove(task);
        return task;
    }

    @Override
    public TaskEntity update(TaskEntity task) {
        // merge() on an id that no longer exists INSERTs rather than failing, resurrecting a
        // deleted task. Checking first inside the caller's transaction closes that; a delete
        // committing after this point is caught by Hibernate's row-count check at flush.
        if (em.find(TaskEntity.class, task.getId()) == null)
            throw new TaskNotFoundException(task.getId());
        return em.merge(task);
    }

    @Override
    public TaskEntity findById(int id) {
        TaskEntity task = em.find(TaskEntity.class, id);
        // em.find returns null for a missing row; the interface promises an exception.
        if (task == null)
            throw new TaskNotFoundException(id);
        return task;
    }

    @Override
    public List<TaskEntity> findByTitle(String title) {
        // upper(): the in-memory store matched titles case-insensitively and this one did not.
        TypedQuery<TaskEntity> query = em.createQuery(
                "select t from TaskEntity t where upper(t.title) = upper(:title) order by t.createdAt desc",
                TaskEntity.class);
        query.setParameter("title", title);
        return query.getResultList();
    }

    @Override
    public List<TaskEntity> findByDone(boolean done) {
        TypedQuery<TaskEntity> query = em.createQuery(
                "select t from TaskEntity t where t.done = :done order by t.createdAt desc",
                TaskEntity.class);
        query.setParameter("done", done);
        return query.getResultList();
    }

    @Override
    public List<TaskEntity> findAll() {
        TypedQuery<TaskEntity> query = em.createQuery(
                "select t from TaskEntity t order by t.createdAt desc", TaskEntity.class);
        return query.getResultList();
    }
}
