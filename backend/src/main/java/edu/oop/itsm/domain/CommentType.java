package edu.oop.itsm.domain;

public enum CommentType {
    COMMENT, INVESTIGATION_NOTE, INFO_REQUEST, INFO_RESPONSE, RESOLUTION, SYSTEM;

    /** Investigation notes are technician-internal and hidden from the reporter. */
    public boolean isInternal() { return this == INVESTIGATION_NOTE; }
}
