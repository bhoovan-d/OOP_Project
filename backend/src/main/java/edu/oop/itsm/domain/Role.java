package edu.oop.itsm.domain;

public enum Role {
    EMPLOYEE("E"), TECHNICIAN("T"), ADMIN("A");

    private final String codePrefix;

    Role(String codePrefix) { this.codePrefix = codePrefix; }

    public String codePrefix() { return codePrefix; }
}
