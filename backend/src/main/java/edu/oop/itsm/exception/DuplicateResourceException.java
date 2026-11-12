package edu.oop.itsm.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends ItsmException {
    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "DUPLICATE", message);
    }
}
