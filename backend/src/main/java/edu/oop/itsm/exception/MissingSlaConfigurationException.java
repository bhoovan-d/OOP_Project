package edu.oop.itsm.exception;

import org.springframework.http.HttpStatus;

public class MissingSlaConfigurationException extends ItsmException {
    public MissingSlaConfigurationException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "MISSING_SLA_CONFIGURATION", message);
    }
}
