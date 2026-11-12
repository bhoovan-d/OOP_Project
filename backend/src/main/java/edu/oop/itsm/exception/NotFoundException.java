package edu.oop.itsm.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ItsmException {
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
