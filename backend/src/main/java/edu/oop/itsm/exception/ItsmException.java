package edu.oop.itsm.exception;

import org.springframework.http.HttpStatus;

/** Root of all business exceptions. Each subtype carries the HTTP status it maps to. */
public abstract class ItsmException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    protected ItsmException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
