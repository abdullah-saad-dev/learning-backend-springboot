package com.example.demo;

import com.example.demo.auth.exceptions.DuplicateEmailsException;
import com.example.demo.tasks.exceptions.InvalidAssigneeException;
import com.example.demo.tasks.exceptions.TaskConflictException;
import com.example.demo.tasks.exceptions.TaskNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Lowest precedence on purpose: Spring's own ProblemDetails advice must get first refusal on
 * the exceptions it already renders (malformed JSON, failed @Valid), or the catch-all below
 * would turn every one of those 400s into a 500.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    public ProblemDetail handle(TaskNotFoundException ex) {
        log.atDebug()
                .setMessage("task not found")
                .addKeyValue("error", ex.getMessage())
                .log();
        return problem(HttpStatus.NOT_FOUND, "Task not found", ex.getMessage());
    }

    @ExceptionHandler(InvalidAssigneeException.class)
    public ProblemDetail handle(InvalidAssigneeException ex) {
        log.atDebug()
                .setMessage("assignee rejected")
                .addKeyValue("error", ex.getMessage())
                .log();
        return problem(HttpStatus.BAD_REQUEST, "Invalid assignee", ex.getMessage());
    }

    /**
     * The caller edited a stale copy: their version is behind the stored one.
     */
    @ExceptionHandler(TaskConflictException.class)
    public ProblemDetail handle(TaskConflictException ex) {
        log.atDebug()
                .setMessage("stale write rejected")
                .addKeyValue("error", ex.getMessage())
                .log();
        return problem(HttpStatus.CONFLICT, "Conflicting change", ex.getMessage());
    }

    /**
     * The same conflict, caught one layer down: a writer that committed inside this
     * transaction's window, so the version was still current when we checked it and stale by
     * the time we flushed. Same answer to the client - re-read and retry.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handle(ObjectOptimisticLockingFailureException ex) {
        log.atDebug()
                .setMessage("optimistic lock failed at flush")
                .addKeyValue("error", ex.getMessage())
                .log();
        return problem(HttpStatus.CONFLICT, "Conflicting change",
                "The entity changed while this request was in flight. Re-read it and retry.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        log.atDebug()
                .setMessage("authentication rejected")
                .addKeyValue("exception", ex.getClass().getSimpleName())
                .addKeyValue("error", ex.getMessage())
                .log();
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Bad credentials, please try again later");
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(org.springframework.security.access.AccessDeniedException ex) {
        log.atDebug()
                .setMessage("authorization rejected")
                .addKeyValue("error", ex.getMessage())
                .log();
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "You are not authorized to perform this operation");
    }

    @ExceptionHandler(DuplicateEmailsException.class)
    public ProblemDetail handle(DuplicateEmailsException ex) {
        log.atInfo()
                .setMessage("signup rejected, email already registered")
                .addKeyValue("email", ex.getEmail())
                .log();
        return problem(HttpStatus.CONFLICT, "Conflicting change",
                "The email is already registered, please try again later");
    }

    /**
     * Backstop for a constraint this code did not anticipate. Reaching here means some write
     * escaped validation and the database refused it, so the stack trace is kept - unlike the
     * client errors above, there is something to diagnose.
     * <p>
     * WARN rather than ERROR because a caller can still trigger it, so it must not page anyone;
     * WARN rather than DEBUG because it means a missing check, not a user mistake.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handle(DataIntegrityViolationException ex) {
        log.warn("unhandled constraint violation", ex);
        return problem(HttpStatus.CONFLICT, "Conflicting change",
                "The request conflicts with data that already exists.");
    }

    /**
     * Anything unhandled. spring.mvc.problemdetails covers only Spring's own exceptions, so
     * without this an unexpected failure escapes as Boot's default error body and breaks the
     * one content type the API otherwise promises.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handle(Exception ex) {
        log.error("unhandled exception serving request", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "The request could not be completed.");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        return p;
    }
}
