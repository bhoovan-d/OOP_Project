package edu.oop.itsm.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ItsmException {
    public InvalidCredentialsException(String message) {
        super(HttpStatus.UNAUTHORIZED, "INVALID_LOGIN", message);
    }
}
