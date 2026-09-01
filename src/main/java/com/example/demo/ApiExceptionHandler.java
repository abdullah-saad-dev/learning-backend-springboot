package com.example.demo;

import com.example.demo.auth.exceptions.DuplicateEmailsException;
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

    // Client errors below are logged at DEBUG on purpose. Each one is a caller mistake the
    // caller can already see in the response, and each is reachable by anyone at any rate, so
    // a level that is on in production would let a stranger set the volume of your log.

    @ExceptionHandler(TaskNotFoundException.class)
    public ProblemDetail handle(TaskNotFoundException ex) {
        log.debug("task not found: {}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, "Task not found", ex.getMessage());
    }

    /**
     * The caller edited a stale copy: their version is behind the stored one.
     */
    @ExceptionHandler(TaskConflictException.class)
    public ProblemDetail handle(TaskConflictException ex) {
        log.debug("stale write rejected: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, "Conflicting change", ex.getMessage());
    }

    /**
     * The same conflict, caught one layer down: a writer that committed inside this
     * transaction's window, so the version was still current when we checked it and stale by
     * the time we flushed. Same answer to the client - re-read and retry.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handle(ObjectOptimisticLockingFailureException ex) {
        // Distinct message from the one above so the two layers stay tellable apart in the log:
        // this is the narrower race the service's own version check cannot see.
        log.debug("optimistic lock failed at flush: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, "Conflicting change",
                "The task changed while this request was in flight. Re-read it and retry.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        // Type as well as message: this one handler covers a bad password and every refresh
        // failure, and the class name is the only thing here that tells them apart. The
        // interesting refresh cases already logged themselves in RefreshTokenService with the
        // ids attached, so this line stays thin rather than repeating them.
        log.debug("authentication rejected [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Bad credentials, please try again later");
    }

    @ExceptionHandler(DuplicateEmailsException.class)
    public ProblemDetail handle(DuplicateEmailsException ex) {
        // INFO, not WARN: nobody is woken by someone re-using their own email, and signup is
        // open to the world, so a WARN here is a stream anyone can turn on. Kept at INFO rather
        // than DEBUG because repeated hits on distinct addresses is how account enumeration
        // looks, and that is worth having on by default. The address is deliberate and is the
        // only personal data logged anywhere - it is what makes that pattern visible, and it
        // inherits whatever retention your log store has.
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
     * <p>
     * The detail sent to the client is generic on purpose. The exception message quotes the
     * offending row - "Key (email)=(taken@example.com) already exists" - and echoing that back
     * would hand an unauthenticated caller a way to test which values are present.
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
