package edu.oop.itsm.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedOperationException extends ItsmException {
    public UnauthorizedOperationException(String message) {
        super(HttpStatus.FORBIDDEN, "UNAUTHORIZED_OPERATION", message);
    }
}
