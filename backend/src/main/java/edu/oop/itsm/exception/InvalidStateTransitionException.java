package edu.oop.itsm.exception;

import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends ItsmException {
    public InvalidStateTransitionException(String message) {
        super(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", message);
    }
}
