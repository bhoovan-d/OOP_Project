package edu.oop.itsm.exception;

import org.springframework.http.HttpStatus;

public class TechnicianUnavailableException extends ItsmException {
    public TechnicianUnavailableException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "TECHNICIAN_UNAVAILABLE", message);
    }
}
