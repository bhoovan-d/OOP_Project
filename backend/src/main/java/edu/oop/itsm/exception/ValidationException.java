package edu.oop.itsm.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends ItsmException {
    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message);
    }
}
