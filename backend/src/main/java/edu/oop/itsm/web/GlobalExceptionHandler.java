package edu.oop.itsm.web;

import edu.oop.itsm.exception.ItsmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps every failure to an RFC 7807 problem+json body with a stable machine-readable "code". */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ItsmException.class)
    ProblemDetail business(ItsmException e) {
        return problem(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError f : e.getBindingResult().getFieldErrors()) fields.putIfAbsent(f.getField(), f.getDefaultMessage());
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Please correct the highlighted fields");
        p.setProperty("fields", fields);
        return p;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail unreadable(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Malformed request: check field names, types and allowed values");
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ProblemDetail denied(Exception e) {
        return problem(HttpStatus.FORBIDDEN, "UNAUTHORIZED_OPERATION", "You do not have permission to perform this operation");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail conflict(ObjectOptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "Someone else updated this record at the same time. Refresh and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "DUPLICATE", "This conflicts with existing data (duplicate or still referenced)");
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail database(DataAccessException e) {
        log.error("Database failure", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "The database is temporarily unavailable. Please retry shortly.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail noResource(NoResourceFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such endpoint");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong on our side. The error has been logged.");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(status.getReasonPhrase());
        p.setProperty("code", code);
        return p;
    }
}
