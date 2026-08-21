package com.example.demo;

import com.example.demo.tasks.exceptions.TaskConflictException;
import com.example.demo.tasks.exceptions.TaskNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(TaskNotFoundException.class)
    public ProblemDetail handle(TaskNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Task not found", ex.getMessage());
    }

    /** The caller edited a stale copy: their version is behind the stored one. */
    @ExceptionHandler(TaskConflictException.class)
    public ProblemDetail handle(TaskConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Conflicting change", ex.getMessage());
    }

    /**
     * The same conflict, caught one layer down: a writer that committed inside this
     * transaction's window, so the version was still current when we checked it and stale by
     * the time we flushed. Same answer to the client - re-read and retry.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handle(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Conflicting change",
                "The task changed while this request was in flight. Re-read it and retry.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex){
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Bad credentials, please try again later");
    }

    /**
     * Anything unhandled. spring.mvc.problemdetails covers only Spring's own exceptions, so
     * without this an unexpected failure escapes as Boot's default error body and breaks the
     * one content type the API otherwise promises.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handle(Exception ex) {
        log.error("Unhandled exception serving request", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "The request could not be completed.");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        return p;
    }
}
