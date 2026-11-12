package edu.oop.itsm.exception;

import org.springframework.http.HttpStatus;

public class WorkloadExceededException extends ItsmException {
    public WorkloadExceededException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "WORKLOAD_EXCEEDED", message);
    }
}
